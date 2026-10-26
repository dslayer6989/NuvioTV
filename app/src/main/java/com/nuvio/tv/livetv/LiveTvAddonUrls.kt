package com.nuvio.tv.livetv

import java.net.URLEncoder

private const val ManifestSuffix = "/manifest.json"

/** Percent-encodes one URL path segment (spaces become %20, not +). */
internal fun String.encodeAddonPathSegment(): String =
    URLEncoder.encode(this, "UTF-8").replace("+", "%20")

/**
 * Builds `<base>/<resource>/<type>/<id>[/<extra>].json[?query]` from a manifest URL.
 * Any query string on the manifest URL is kept, the way NuvioTV does for other addon calls.
 */
internal fun buildAddonResourceUrl(
    manifestUrl: String,
    resource: String,
    type: String,
    id: String,
    extraPathSegment: String? = null,
): String {
    val trimmed = manifestUrl.trim()
    val queryIndex = trimmed.indexOf('?')
    val pathPart = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
    val query = if (queryIndex >= 0) trimmed.substring(queryIndex) else ""
    val base = pathPart.removeSuffix(ManifestSuffix).trimEnd('/')
    val extra = extraPathSegment?.takeIf { it.isNotEmpty() }?.let { "/$it" }.orEmpty()
    return "$base/$resource/${type.encodeAddonPathSegment()}/${id.encodeAddonPathSegment()}$extra.json$query"
}
