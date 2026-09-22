package com.ledgerflow.realtime;

/** The one naming convention every piece of the SSE fan-out agrees on: one Redis pub/sub channel per organization. */
final class RealtimeChannels {

    private static final String PREFIX = "rt:org:";
    static final String PATTERN = PREFIX + "*";

    private RealtimeChannels() {}

    static String forOrg(long orgId) {
        return PREFIX + orgId;
    }

    static long orgIdFromChannel(String channel) {
        return Long.parseLong(channel.substring(PREFIX.length()));
    }
}
