package jndc.core;

import jndc.core.data_store.DBWrapper;
import jndc.server.IpFilterRecord;
import jndc.server.IpFilterRule4V;
import jndc.utils.UUIDSimple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class IpChecker {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private volatile LinkedBlockingQueue<IpRecord> recordQueue = new LinkedBlockingQueue<>();

    private Map<String, IpFilterRule4V> blackMap = new HashMap<>();

    private Map<String, IpFilterRule4V> whiteMap = new HashMap<>();//higher priority

    private ExecutorService executorService;

    //release ip map
    private volatile Map<String, IPCount> releaseMap = new ConcurrentHashMap<>();

    //block ip map
    private volatile Map<String, IPCount> blockMap = new ConcurrentHashMap<>();

    private volatile boolean work = true;

    private volatile boolean pause = false;

    private final long IP_CACHE_EXPIRE=24*60*60*1000;





    public void storeRecordData() {
        DBWrapper<IpFilterRecord> dbWrapper = DBWrapper.getDBWrapper(IpFilterRecord.class);
        List<IpFilterRecord> list = new ArrayList<>();
        releaseMap.forEach((k, v) -> {
            IpFilterRecord ipFilterRecord = v.toIpFilterRecord();
            if (ipFilterRecord.getvCount()>0){
                ipFilterRecord.setRecordType(IpRecord.RELEASE_STATE);
                ipFilterRecord.setId(UUIDSimple.id());
                list.add(ipFilterRecord);
                v.reset();
            }else {
                //todo remove expire key
                long timeStamp = ipFilterRecord.getTimeStamp();
                if (timeStamp+IP_CACHE_EXPIRE<System.currentTimeMillis()){
                    releaseMap.remove(k);
                }
            }
        });

        blockMap.forEach((k, v) -> {
            IpFilterRecord ipFilterRecord = v.toIpFilterRecord();
            if (ipFilterRecord.getvCount()>0){
                ipFilterRecord.setRecordType(IpRecord.BLOCK_STATE);
                ipFilterRecord.setId(UUIDSimple.id());
                list.add(ipFilterRecord);
                v.reset();
            }else {
                //todo remove expire key
                long timeStamp = ipFilterRecord.getTimeStamp();
                if (timeStamp+IP_CACHE_EXPIRE<System.currentTimeMillis()){
                    blockMap.remove(k);
                }
            }
        });

        dbWrapper.insertBatch(list);

    }

    public IpChecker() {
        executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> {
            while (work) {
                if (!pause) {
                    try {
                        IpRecord take = recordQueue.take();
                        if (take.isRelease()) {
                            addToMap(take, releaseMap);
                        } else {
                            addToMap(take, blockMap);
                        }
                    } catch (InterruptedException e) {
                        logger.error("recordQueue：" + e);
                    }
                }
            }
        });
    }


    private void addToMap(IpRecord take, Map<String, IPCount> map) {
        String ip = take.getIp();
        IPCount ipCount = map.get(ip);
        if (ipCount == null) {
            ipCount = new IPCount(ip);
            map.put(ip, ipCount);
        }
        ipCount.increase();
    }

    public void loadRule(Map<String, IpFilterRule4V> blackMap, Map<String, IpFilterRule4V> whiteMap) {
        this.blackMap = blackMap;
        this.whiteMap = whiteMap;
    }


    public boolean checkIpAddress(String ipAddress) {

        //if ip white is not empty
        if (whiteMap.size() > 0) {
            if (whiteMap.containsKey(ipAddress)) {

                //do logging
                try {
                    recordQueue.put(new IpRecord(ipAddress, IpRecord.RELEASE_STATE));
                } catch (InterruptedException e) {
                    logger.error("releaseQueue：" + e);
                }

                //release request
                return true;
            } else {

                //do logging
                try {
                    recordQueue.put(new IpRecord(ipAddress, IpRecord.BLOCK_STATE));
                } catch (InterruptedException e) {
                    logger.error("blockQueue：" + e);
                }

                //block request
                return false;
            }
        }


        //do not perform blacklist matching if there is a whitelist
        if (blackMap.containsKey(ipAddress)) {
            //do logging
            try {
                recordQueue.put(new IpRecord(ipAddress, IpRecord.BLOCK_STATE));
            } catch (InterruptedException e) {
                logger.error("blockQueue：" + e);
            }

            //block request
            return false;
        } else {
            //do logging
            try {
                recordQueue.put(new IpRecord(ipAddress, IpRecord.RELEASE_STATE));
            } catch (InterruptedException e) {
                logger.error("releaseQueue：" + e);
            }

            //release request
            return true;
        }
    }

    /* --------------getter and setter-------------- */

  /*  public Map<String, List<IpRecord>> getReleaseMap() {
        return releaseMap;
    }

    public Map<String, List<IpRecord>> getBlockMap() {
        return blockMap;
    }*/

    public Map<String, IpFilterRule4V> getBlackMap() {
        return blackMap;
    }

    public Map<String, IpFilterRule4V> getWhiteMap() {
        return whiteMap;
    }


    public class IPCount {
        private String ip;
        private AtomicInteger count = new AtomicInteger();
        private long lastTimeStamp;


        public IpFilterRecord toIpFilterRecord() {
            IpFilterRecord ipFilterRecord = new IpFilterRecord();
            ipFilterRecord.setIp(ip);
            ipFilterRecord.setvCount(count.get());
            ipFilterRecord.setTimeStamp(lastTimeStamp);
            return ipFilterRecord;
        }

        public IPCount(String ip) {
            this.ip = ip;
        }

        public void increase() {
            count.incrementAndGet();
            timeTag();
        }

        /**
         * record last edit timestamp
         */
        private void timeTag() {
            this.lastTimeStamp = System.currentTimeMillis();
        }

        public void reset() {
            count.set(0);
        }
    }

    public class IpRecord {

        public static final int RELEASE_STATE = 0;
        public static final int BLOCK_STATE = 1;

        private String ip;

        private int tag;//0 release 1 block


        public boolean isRelease() {
            return tag == 0;
        }


        public IpRecord(String ip, int tag) {
            this.ip = ip;
            this.tag = tag;
        }


        /* --------------getter and setter-------------- */

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

    }


}
