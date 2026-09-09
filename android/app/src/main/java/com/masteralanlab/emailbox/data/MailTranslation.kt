package com.masteralanlab.emailbox.data

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class EnglishIdentification(
    val candidate: EnglishBodyDetector.Candidate,
    val confidence: Float,
)

/** ML Kit remains on-device; no email body is sent to a translation API. */
object MailTranslation {
    suspend fun identifyEnglish(body: String): EnglishIdentification? {
        val candidate = EnglishBodyDetector.candidate(body) ?: return null
        val identifier = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(EnglishBodyDetector.MIN_LANGUAGE_CONFIDENCE)
                .build(),
        )
        return try {
            identify(identifier, candidate)?.let { confidence ->
                EnglishIdentification(candidate, confidence)
            }
        } finally {
            identifier.close()
        }
    }

    suspend fun translateEnglishToChinese(text: String): String {
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build(),
        )
        return try {
            translator.downloadModelIfNeeded(
                DownloadConditions.Builder().requireWifi().build(),
            ).awaitTask()
            translator.translate(text).awaitTask()
        } finally {
            translator.close()
        }
    }

    suspend fun deleteEnglishChineseModel() {
        RemoteModelManager.getInstance()
            .deleteDownloadedModel(TranslateRemoteModel.Builder(TranslateLanguage.CHINESE).build())
            .awaitTask()
    }

    private suspend fun identify(
        identifier: com.google.mlkit.nl.languageid.LanguageIdentifier,
        candidate: EnglishBodyDetector.Candidate,
    ): Float? = identifier.identifyPossibleLanguages(candidate.text.take(200))
        .awaitTask()
        .firstOrNull { it.languageTag == "en" }
        ?.confidence
        ?.takeIf { it >= EnglishBodyDetector.MIN_LANGUAGE_CONFIDENCE }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value)
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
    addOnCanceledListener { continuation.cancel() }
}
