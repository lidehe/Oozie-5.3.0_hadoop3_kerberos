/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.oozie.action.hadoop;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.oozie.DagELFunctions;
import org.apache.oozie.ErrorCode;
import org.apache.oozie.action.ActionExecutorException;
import org.apache.oozie.client.WorkflowJob;
import org.apache.oozie.service.ConfigurationService;
import org.apache.oozie.service.HadoopAccessorException;
import org.apache.oozie.service.Services;
import org.apache.oozie.service.HadoopAccessorService;
import org.apache.oozie.util.XConfiguration;

/**
 * EL function for fs action executor.
 */
public class FsELFunctions {
    static final String FS_EL_FUNCTIONS_CONF = "FsELFunctions.conf.fs.";

    private static FileSystem getFileSystem(URI uri) throws HadoopAccessorException {
        WorkflowJob workflow = DagELFunctions.getWorkflow();
        String user = workflow.getUser();
        HadoopAccessorService has = Services.get().get(HadoopAccessorService.class);
        Configuration conf = has.createConfiguration(uri.getAuthority());

        extractExtraFsConfiguration(workflow, conf, uri);

        return has.createFileSystem(user, uri, conf);
    }

    static void extractExtraFsConfiguration(WorkflowJob workflow, Configuration conf, URI uri)
            throws HadoopAccessorException {
        if (workflow.getConf() != null) {
            try {
                readFsConfigFromOozieSite(conf, uri);
                readFsConfigFromWorkflow(workflow, conf, uri);
            } catch (Exception e) {
                throw new HadoopAccessorException(ErrorCode.E0759, e);
            }
        }
    }

    private static void readFsConfigFromOozieSite(Configuration conf, URI uri) {
        final String fsElFunctionsConfWithScheme = FS_EL_FUNCTIONS_CONF + uri.getScheme();
        final String customELFsProperties = ConfigurationService.get(fsElFunctionsConfWithScheme);

        for (final String entry : customELFsProperties.split(",")) {
            final String[] nameAndValue = entry.trim().split("=", 2);
            if (nameAndValue.length < 2) {
                continue;
            }
            putKeyToConfIfAllowed(conf, nameAndValue[0], nameAndValue[1]);
        }
    }

    private static void readFsConfigFromWorkflow(WorkflowJob workflow, Configuration conf, URI uri) throws Exception {
        if (workflow.getConf() == null) {
            return;
        }
        final String FS_EL_FUNCTIONS_CONF_WITH_SCHEME = FS_EL_FUNCTIONS_CONF + uri.getScheme() + ".";
        final XConfiguration workflowConf = new XConfiguration(new StringReader(workflow.getConf()));
        for (Object _key : workflowConf.toProperties().keySet()) {
            String key = (String) _key;
            if (!key.startsWith(FS_EL_FUNCTIONS_CONF_WITH_SCHEME)) {
                continue;
            }
            putKeyToConfIfAllowed(conf, key.substring(FS_EL_FUNCTIONS_CONF_WITH_SCHEME.length()), workflowConf.get(key));
        }
    }

    private static void putKeyToConfIfAllowed(Configuration conf, String key, String value) {
        if (!JavaActionExecutor.DISALLOWED_PROPERTIES.contains(key)) {
            conf.set(key, value);
        }
    }

    /**
     * Get file status.
     *
     * @param pathUri fs path uri
     * @return file status
     * @throws URISyntaxException if pathUri is not a proper URI
     * @throws IOException in case of file system issue
     * @throws Exception in case of file system issue
     */
    private static FileStatus getFileStatus(String pathUri) throws Exception {
        Path path = new Path(pathUri);
        FileSystem fs = getFileSystem(path.toUri());
        return fs.exists(path) ? fs.getFileStatus(path) : null;
    }

    /**
     * Return if a path exists.
     *
     * @param pathUri file system path uri.
     * @return <code>true</code> if the path exists, <code>false</code> if it does not.
     * @throws Exception in case of file system issue
     */
    public static boolean fs_exists(String pathUri) throws Exception {
        Path path = new Path(pathUri);
        FileSystem fs = getFileSystem(path.toUri());
        FileStatus[] pathArr;
        try {
            pathArr = fs.globStatus(path, new FSPathFilter());
        }
        catch (ReachingGlobMaxException e) {
            throw new ActionExecutorException(ActionExecutorException.ErrorType.ERROR, "FS013",
                    "too many globbed files/dirs to do FS operation");
        }
        return (pathArr != null && pathArr.length > 0);
    }

    /**
     * Return if a path is a directory.
     *
     * @param pathUri fs path uri.
     * @return <code>true</code> if the path exists and it is a directory, <code>false</code> otherwise.
     * @throws Exception in case of file system issue
     */
    public static boolean fs_isDir(String pathUri) throws Exception {
        boolean isDir = false;
        FileStatus fileStatus = getFileStatus(pathUri);
        if (fileStatus != null) {
            isDir = fileStatus.isDirectory();
        }
        return isDir;
    }

    /**
     * Return the len of a file.
     *
     * @param pathUri file system path uri.
     * @return the file len in bytes, -1 if the file does not exist or if it is a directory.
     * @throws Exception in case of file system issue
     */
    public static long fs_fileSize(String pathUri) throws Exception {
        long len = -1;
        FileStatus fileStatus = getFileStatus(pathUri);
        if (fileStatus != null) {
            len = fileStatus.getLen();
        }
        return len;
    }

    /**
     * Return the size of all files in the directory, it is not recursive.
     *
     * @param pathUri file system path uri.
     * @return the size of all files in the directory, -1 if the directory does not exist or if it is a file.
     * @throws Exception in case of file system issue
     */
    public static long fs_dirSize(String pathUri) throws Exception {
        Path path = new Path(pathUri);
        long size = -1;
        try {
            FileSystem fs = getFileSystem(path.toUri());
            if (fs.exists(path) && !fs.isFile(path)) {
                FileStatus[] stati = fs.listStatus(path);
                size = 0;
                if (stati != null) {
                    for (FileStatus status : stati) {
                        if (!status.isDirectory()) {
                            size += status.getLen();
                        }
                    }
                }
            }
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return size;
    }

    /**
     * Return the file block size in bytes.
     *
     * @param pathUri file system path uri.
     * @return the block size of the file in bytes, -1 if the file does not exist or if it is a directory.
     * @throws Exception in case of file system issue
     */
    public static long fs_blockSize(String pathUri) throws Exception {
        long blockSize = -1;
        FileStatus fileStatus = getFileStatus(pathUri);
        if (fileStatus != null) {
            blockSize = fileStatus.getBlockSize();
        }
        return blockSize;
    }

    static class FSPathFilter implements PathFilter {
        int count = 0;
        int globMax = Integer.MAX_VALUE;
        public FSPathFilter() {
            globMax = ConfigurationService.getInt(LauncherAMUtils.CONF_OOZIE_ACTION_FS_GLOB_MAX);
        }
        @Override
        public boolean accept(Path p) {
            count++;
            if(count > globMax) {
                throw new ReachingGlobMaxException();
            }
            return true;
        }
    }

    /**
     * ReachingGlobMaxException thrown when globbed file count exceeds the limit
     */
    static class ReachingGlobMaxException extends RuntimeException {
    }

}
