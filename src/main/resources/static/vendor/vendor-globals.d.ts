/**
 * Re-definition of actually used functions.
 */
declare namespace bootstrap {
    class Collapse {
        static getOrCreateInstance(element: Element): Collapse;
        show(): void;
        hide(): void;
    }
}
