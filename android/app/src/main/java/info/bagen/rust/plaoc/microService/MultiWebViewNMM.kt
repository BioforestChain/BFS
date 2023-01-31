package info.bagen.rust.plaoc.microService
import info.bagen.rust.plaoc.App.Companion.mainActivity
import info.bagen.rust.plaoc.webView.openDWebWindow

typealias code = String

data class WindowOptions(
    val processId: Int?,  // 要挂载的父进程id
    val origin: String = "",
    val main_js: String? = ""
)


class MultiWebViewNMM(override val mmid: String = "mwebview.sys.dweb") : NativeMicroModule() {

    private var viewTree: ViewTree = ViewTree()
    private val routers: Router = mutableMapOf()

    init {
        // 注册路由
        routers["/open"] = put@{
            return@put openDwebView(it as WindowOptions)
        }
        routers["/evalJavascript/(:webview_id)"] = put@{
            return@put true
        }
    }

    private fun openDwebView(args: WindowOptions): Number {
        println("Kotlin#MultiWebViewNMM openDwebView $args")
        val webviewNode = viewTree.createNode(args)
        viewTree.appendTo(webviewNode)
        // openDwebView
        if (mainActivity !== null) {
            openDWebWindow( activity = mainActivity!!.getContext(),url = args.origin)
        }
        return webviewNode.id
    }

    private fun closeDwebView(nodeId:Int): Boolean {
      return  this.viewTree.removeNode(nodeId)
    }
}

class ViewTree {
    val root = ViewTreeStruct(0, 0,"", mutableListOf())
    private val currentProcess = 0

    data class ViewTreeStruct(
        val id: Int,
        val processId: Int, //processId as parentId
        val origin:String,
        val children: MutableList<ViewTreeStruct?>
    )

    fun createNode(args: WindowOptions): ViewTreeStruct {
        var processId = currentProcess
        //  当用户传递了processId，即明确需要挂载到某个view下
        if (args.processId !== null) {
            processId = args.processId
        }
        return ViewTreeStruct(
            id = processId + 1,
            processId = processId, // self add node id
            origin = args.origin,
            children = mutableListOf()
            )
    }

    fun appendTo(webviewNode: ViewTreeStruct): Int {
        val processId =webviewNode.processId
        fun next(node:ViewTreeStruct): Int {
            // 找到加入节点
            if (node.processId == processId) {
                webviewNode.children.add(webviewNode)
                return webviewNode.id
            }
            // 当节点还是小于当前父节点，就还需要BFS查找
            if (node.processId < processId) {
                for (n in node.children) {
                    return next(n as ViewTreeStruct)
                }
            }
            return 0
        }
        // 尾递归
        return next(this.root)
    }

    /**
     * 简单的移除节点
     */
    fun removeNode(nodeId:Int): Boolean {
        fun next(node:ViewTreeStruct): Boolean {
            for (n in node.children) {
                // 找到移除的节点
                if (n?.id == nodeId) {
                  return node.children.remove(n)
                }
            }
            // 当节点还是小于当前父节点，就还需要BFS查找
            if (node.processId < nodeId) {
                for (n in node.children) {
                    return next(n as ViewTreeStruct)
                }
            }
            return  false
        }
        // 尾递归
        return next(this.root)
    }
}