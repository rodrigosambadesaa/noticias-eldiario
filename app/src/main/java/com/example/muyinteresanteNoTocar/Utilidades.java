package com.example.muyinteresanteNoTocar;

import java.io.File;
import android.content.Context;

/** Caché privada de la aplicación; no necesita permisos de almacenamiento externo. */
public class Utilidades {

    static File getDiretorioCache(Context contexto) {
        return contexto != null ? contexto.getCacheDir() : null;
    }
}
