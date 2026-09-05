package com.example.muyinteresante.util;

/** Decisions shared by remote request callers and kept free of Android state. */
public final class ConnectivityRequestPolicy {

    private ConnectivityRequestPolicy() { }

    public static boolean canStartRemoteRequest(boolean usableNetwork) {
        return usableNetwork;
    }

    public static boolean shouldDiagnoseAfterFailure(boolean validHttpResponse,
                                                     boolean ambiguousConnectivityFailure) {
        return !validHttpResponse && ambiguousConnectivityFailure;
    }

    public static String classifyFeedFailure(boolean generalInternetReachable) {
        return generalInternetReachable
                ? "El feed de noticias no está disponible en este momento."
                : "Problema de conectividad. No se ha podido demostrar acceso general a Internet.";
    }
}
