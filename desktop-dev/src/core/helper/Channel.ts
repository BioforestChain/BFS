import { streamRead } from "../../helper/stream/readableStreamHelper.ts";

/**模拟kotlin Channel */
export class Channel<T = Uint8Array> {
  private controller!: ReadableStreamDefaultController<T>;

  private channel = new ReadableStream<T>({
    start: (controller) => {
      this.controller = controller;
    },
  });

  get stream() {
    return this.channel;
  }

  private _isClosedForSend = false;
  get isClosedForSend() {
    return this._isClosedForSend;
  }

  send(value: T) {
    if (this._isClosedForSend) {
      console.error("Channel send is close!!");
      return;
    }
    this.controller.enqueue(value);
  }

  closeWrite() {
    this._isClosedForSend = true;
  }

  close() {
    this.closeWrite();
    this.controller.close();
  }

  [Symbol.asyncIterator](): AsyncIterator<T> {
    return streamRead(this.channel);
  }
}
