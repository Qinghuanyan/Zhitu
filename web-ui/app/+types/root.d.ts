export namespace Route {
  export type LinksFunction = () => Array<{
    rel?: string;
    href: string;
    type?: string;
    sizes?: string;
  }>;

  export interface ErrorBoundaryProps {
    error: unknown;
  }
}
