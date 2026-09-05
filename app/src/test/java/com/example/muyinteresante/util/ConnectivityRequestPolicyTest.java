package com.example.muyinteresante.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectivityRequestPolicyTest {

    @Test
    public void offlineGuardStopsRemoteRequestImmediately() {
        assertFalse(ConnectivityRequestPolicy.canStartRemoteRequest(false));
    }

    @Test
    public void usableNetworkAllowsRealRequest() {
        assertTrue(ConnectivityRequestPolicy.canStartRemoteRequest(true));
    }

    @Test
    public void validHttpResponseNeverTriggersGeneralDiagnostic() {
        assertFalse(ConnectivityRequestPolicy.shouldDiagnoseAfterFailure(true, true));
    }

    @Test
    public void ambiguousFeedFailureWithGeneralInternetMeansFeedUnavailable() {
        assertTrue(ConnectivityRequestPolicy.shouldDiagnoseAfterFailure(false, true));
        assertEquals("El feed de noticias no está disponible en este momento.",
                ConnectivityRequestPolicy.classifyFeedFailure(true));
    }

    @Test
    public void ambiguousFeedFailureWithoutGeneralInternetMeansConnectivityProblem() {
        assertTrue(ConnectivityRequestPolicy.shouldDiagnoseAfterFailure(false, true));
        assertEquals("Problema de conectividad. No se ha podido demostrar acceso general a Internet.",
                ConnectivityRequestPolicy.classifyFeedFailure(false));
    }
}
