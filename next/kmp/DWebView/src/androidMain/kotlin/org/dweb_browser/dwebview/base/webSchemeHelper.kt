package org.dweb_browser.dwebview.base

val urlScheme = setOf(
  "about",
  "acap",
  "addbook",
  "afp",
  "afs",
  "aim",
  "applescript",
  "bcp",
  "bk",
  "btspp",
  "callto",
  "castanet",
  "cdv",
  "chrome",
  "chttp",
  "cid",
  "crid",
  "data",
  "dav",
  "daytime",
  "device",
  "dict",
  "dns",
  "doi",
  "dtn",
  "ed2k",
  "eid",
  "enp",
  "fax",
  "feed",
  "file",
  "finger",
  "freenet",
  "ftp",
  "go",
  "gopher",
  "gsiftp",
  "gsm-sms",
  "h323",
  "h324",
  "hdl",
  "hnews",
  "http",
  "https",
  "httpsy",
  "iioploc",
  "ilu",
  "im",
  "imap",
  "info",
  "IOR",
  "ip",
  "ipp",
  "irc",
  "iris.beep",
  "itms",
  "jar",
  "javascript",
  "jdbc",
  "klik",
  "kn",
  "lastfm",
  "ldap",
  "lifn",
  "livescript",
  "lrq",
  "mac",
  "magnet",
  "mailbox",
  "mailserver",
  "mailto",
  "man",
  "md5",
  "mid",
  "mms",
  "mocha",
  "modem",
  "moz-abmdbdirectory",
  "msni",
  "mtqp",
  "mumble",
  "mupdate",
  "myim",
  "news",
  "nltk",
  "nfs",
  "nntp",
  "oai",
  "opaquelocktoken",
  "pcast",
  "phone",
  "php",
  "pop",
  "pop3",
  "pres",
  "printer",
  "prospero",
  "pyimp",
  "rdar",
  "res",
  "rtsp",
  "rvp",
  "rwhois",
  "rx",
  "sdp",
  "secondlife",
  "service",
  "sip",
  "sips",
  "smb",
  "smtp",
  "snews",
  "snmp",
  "soap.beep",
  "soap.beeps",
  "soap.udp",
  "SubEthaEdit",
  "svn",
  "svn+ssh",
  "t120",
  "tag",
  "tann",
  "tcp",
  "tel",
  "telephone",
  "telnet",
  "tftp",
  "thismessage",
  "tip",
  "tn3270",
  "tv",
  "txmt",
  "uddi",
  "urn",
  "uuid",
  "vemmi",
  "videotex",
  "view-source",
  "wais",
  "wcap",
  "webcal",
  "whodp",
  "whois",
  "whois++",
  "wpn",
  "wtai",
  "xeerkat",
  "xfire",
  "xmlrpc.beep",
  "xmlrpc.beeps",
  "xmpp",
  "ymsgr",
  "z39.50r",
  "z39.50s",
)

fun isWebUrlScheme(scheme: String) =
  scheme == "http" || scheme == "https" || urlScheme.contains(scheme) || scheme.startsWith("web+")

