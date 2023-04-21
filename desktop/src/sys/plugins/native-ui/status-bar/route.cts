import type { $BaseRoute } from "../../base/base-add-routes-to-http.cjs"
export const routes :$BaseRoute[] = [
    {
        pathname: "/status-bar-ui/wait_for_operation",
        matchMode: "full",
        method: "GET"
    },
    {
        pathname: "/status-bar-ui/operation_return",
        matchMode: "full",
        method: "POST"
    },
    {
        pathname: "/status-bar.nativeui.sys.dweb/startObserve",
        matchMode: "prefix",
        method: "GET"
    },
    {
        pathname: "/status-bar.nativeui.sys.dweb/stopObserve",
        matchMode: "prefix",
        method: "GET"
    },
    {
        pathname:"/status-bar.nativeui.sys.dweb/getState",
        matchMode: "prefix",
        method: "GET"
    },
    {
        pathname:"/status-bar.nativeui.sys.dweb/setState",
        matchMode: "prefix",
        method: "GET"
    },
    {
        // /internal/observe?X-Dweb-Host=api.browser.sys.dweb%3A443&mmid=status-bar.nativeui.sys.dweb
        pathname:"/status-bar.nativeui.sys.dweb/internal/observe",
        matchMode: "full",
        method: "GET"
    }
]


