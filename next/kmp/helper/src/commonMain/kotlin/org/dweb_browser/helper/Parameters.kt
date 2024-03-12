package org.dweb_browser.helper

typealias Parameters = List<Parameter>

fun Parameters.toUrlFormEncoded(): String = joinToString("&") {
  it.first.toFormEncoded() + it.second?.let { "=" + it.toFormEncoded() }.orEmpty()
}

fun Parameters.toParametersMap(): Map<String, List<String?>> =
  groupBy(Parameter::first, Parameter::second)

fun <K, V> Map<K, List<V>>.getFirst(key: K) = this[key]?.firstOrNull()

fun String.toParameters() = if (isNotEmpty()) split("&").map(String::toParameter) else listOf()

fun Parameters.findSingle(name: String): String? = find { it.first == name }?.second

fun Parameters.findMultiple(name: String) = filter { it.first == name }.map { it.second }

private fun String.toParameter(): Parameter = split("=", limit = 2).map(String::fromFormEncoded)
  .let { l -> l.elementAt(0) to l.elementAtOrNull(1) }

internal fun String.fromFormEncoded() = this.encodeURI()

internal fun String.toFormEncoded() = this.decodeURI()

internal typealias Parameter = Pair<String, String?>

fun String.format(vararg args: Any): String {
  var format = this
  args.forEach { arg ->
    format = format.replaceFirst("%s", arg.toString())
  }
  return format
}