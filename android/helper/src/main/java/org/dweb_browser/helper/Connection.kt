package org.dweb_browser.helper

fun rand(start: Int, end: Int): Int {
  require(start <= end) { "Illegal Argument" }
  return (start..end).random()
}







