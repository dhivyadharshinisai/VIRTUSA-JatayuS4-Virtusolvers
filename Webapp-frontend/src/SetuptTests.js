
const noop = () => {};
Object.defineProperty(window, 'WebSocket', {
  writable: true,
  value: class WebSocket {
    constructor() {}
    onopen = noop;
    onclose = noop;
    onerror = noop;
    onmessage = noop;
    close = noop;
    send = noop;
  }
});