// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.imagery;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.jcs.access.behavior.ICacheAccess;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.CachedTileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileJob;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.cache.HostLimitQueue;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Utils;

/**
 * Wrapper class that bridges between JCS cache and Tile Loaders
 *
 * @author Wiktor Niesiobędzki
 */
public class TMSCachedTileLoader implements TileLoader, CachedTileLoader {

    protected final ICacheAccess<String, BufferedImageCacheEntry> cache;
    protected final int connectTimeout;
    protected final int readTimeout;
    protected final Map<String, String> headers;
    protected final TileLoaderListener listener;

    /**
     * overrides the THREAD_LIMIT in superclass, as we want to have separate limit and pool for TMS
     */

    public static final IntegerProperty THREAD_LIMIT = new IntegerProperty("imagery.tms.tmsloader.maxjobs", 25);

    /**
     * Limit definition for per host concurrent connections
     */
    public static final IntegerProperty HOST_LIMIT = new IntegerProperty("imagery.tms.tmsloader.maxjobsperhost", 6);

    /**
     * separate from JCS thread pool for TMS loader, so we can have different thread pools for default JCS
     * and for TMS imagery
     */
    private static final ThreadPoolExecutor DEFAULT_DOWNLOAD_JOB_DISPATCHER = getNewThreadPoolExecutor("TMS-downloader-%d");

    private ThreadPoolExecutor downloadExecutor = DEFAULT_DOWNLOAD_JOB_DISPATCHER;

    /**
     * Constructor
     * @param listener          called when tile loading has finished
     * @param cache              of the cache
     * @param connectTimeout    to remote resource
     * @param readTimeout       to remote resource
     * @param headers           HTTP headers to be sent along with request
     */
    public TMSCachedTileLoader(TileLoaderListener listener, ICacheAccess<String, BufferedImageCacheEntry> cache,
            int connectTimeout, int readTimeout, Map<String, String> headers) {
        CheckParameterUtil.ensureParameterNotNull(cache, "cache");
        this.cache = cache;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.headers = headers;
        this.listener = listener;
    }

    /**
     * @param nameFormat see {@link Utils#newThreadFactory(String, int)}
     * @param workers number of worker thread to keep
     * @return new ThreadPoolExecutor that will use a @see HostLimitQueue based queue
     */
    public static ThreadPoolExecutor getNewThreadPoolExecutor(String nameFormat, int workers) {
        HostLimitQueue workQueue = new HostLimitQueue(HOST_LIMIT.get().intValue());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, // 0 so for unused thread pools threads will eventually die, freeing also the threadpool
                workers, // do not this number of threads
                300, // keepalive for thread
                TimeUnit.SECONDS,
                workQueue,
                Utils.newThreadFactory(nameFormat, Thread.NORM_PRIORITY)
                );
        workQueue.setExecutor(executor);
        return executor;
    }

    /**
     * @param name name of threads
     * @return new ThreadPoolExecutor that will use a @see HostLimitQueue based queue, with default number of threads
     */
    public static ThreadPoolExecutor getNewThreadPoolExecutor(String name) {
        return getNewThreadPoolExecutor(name, THREAD_LIMIT.get().intValue());
    }

    @Override
    public TileJob createTileLoaderJob(Tile tile) {
        return new TMSCachedTileLoaderJob(listener, tile, cache,
                connectTimeout, readTimeout, headers, getDownloadExecutor());
    }

    @Override
    public void clearCache(TileSource source) {
        this.cache.remove(source.getName() + ':');
    }

    /**
     * @return cache statistics as string
     */
    public String getStats() {
        return cache.getStats();
    }

    /**
     * cancels all outstanding tasks in the queue. This rollbacks the state of the tiles in the queue
     * to loading = false / loaded = false
     */
    @Override
    public void cancelOutstandingTasks() {
        for (Runnable r: downloadExecutor.getQueue()) {
            if (downloadExecutor.remove(r) && r instanceof TMSCachedTileLoaderJob) {
                ((TMSCachedTileLoaderJob) r).handleJobCancellation();
            }
        }
    }

    @Override
    public boolean hasOutstandingTasks() {
        return downloadExecutor.getTaskCount() > downloadExecutor.getCompletedTaskCount();
    }

    /**
     * Sets the download executor that will be used to download tiles instead of default one.
     * You can use {@link #getNewThreadPoolExecutor} to create a new download executor with separate
     * queue from default.
     *
     * @param downloadExecutor download executor that will be used to download tiles
     */
    public void setDownloadExecutor(ThreadPoolExecutor downloadExecutor) {
        this.downloadExecutor = downloadExecutor;
    }

    /**
     * @return download executor that is used by this factory
     */
    public ThreadPoolExecutor getDownloadExecutor() {
        return downloadExecutor;
    }
}
