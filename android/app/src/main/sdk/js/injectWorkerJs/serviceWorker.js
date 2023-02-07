class PromiseOut {
  constructor() {
    this.promise = new Promise((resolve, reject) => {
      this.resolve = resolve;
      this.reject = reject;
    })
  }
}
((self) => {
  self.addEventListener("install", (event) => {
    // 跳过等待
    event.waitUntil(self.skipWaiting());
  });
  self.addEventListener("activate", (event) => {
    // 立刻控制整个页面
    event.waitUntil(self.clients.claim());
  });

  // remember event.respondWith must sync call🐰
  self.addEventListener("fetch", (event) => {
    const request = event.request;
    const path = new URL(request.url).pathname;

    console.log("serviceWorker:Fetch==>", path)

    event.respondWith((async () => {
      return fetch(request)
    })());
  });
  // return data 🐯
  self.addEventListener("message", (event) => {
    if (typeof event.data !== "string")
      return;
    console.log("serviceWorker:message", event.data);
  })
})(self);
