import '@testing-library/jest-dom';

// jsdom nem implementálja a ResizeObserver-t — a react-flow (Star Map) ezt
// belsőleg használja a konténer méretének figyeléséhez.
if (typeof globalThis.ResizeObserver === 'undefined') {
  class ResizeObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  globalThis.ResizeObserver = ResizeObserverMock as unknown as typeof ResizeObserver;
}