package info.bagen.dwebbrowser.microService.sys.fileSystem.exeprions

class DirectoryExistsException : Exception {
    constructor(s: String?) : super(s) {}
    constructor(t: Throwable?) : super(t) {}
    constructor(s: String?, t: Throwable?) : super(s, t) {}
}