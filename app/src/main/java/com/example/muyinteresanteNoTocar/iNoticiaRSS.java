package com.example.muyinteresanteNoTocar;

import java.util.ArrayList;

public interface iNoticiaRSS {
	void onRecibeNoticiasRSS(ArrayList<NoticiaRSS> listaNoticias);

	default void onError(DescargaNoticiasRSS.FetchError error) {
		// Optional callback for callers that need to classify remote failures.
	}
}
