import * as React from "react";

import { cn } from "~/lib/utils";

import type { ZhituShellActionPayload, ZhituShellStatePayload } from "./types";

const ZHITU_CHANNEL = "zhitu-shell";

interface ZhituShellFrameProps {
  html: string;
  state: ZhituShellStatePayload;
  onAction: (action: ZhituShellActionPayload) => void;
  className?: string;
}

export function ZhituShellFrame({ html, state, onAction, className }: ZhituShellFrameProps) {
  const iframeRef = React.useRef<HTMLIFrameElement | null>(null);
  const [ready, setReady] = React.useState(false);

  const postState = React.useCallback(() => {
    const frameWindow = iframeRef.current?.contentWindow;
    if (!frameWindow) return;

    frameWindow.postMessage(
      {
        channel: ZHITU_CHANNEL,
        type: "state",
        payload: state,
      },
      "*",
    );
  }, [state]);

  React.useEffect(() => {
    const handleMessage = (event: MessageEvent<ZhituShellActionPayload>) => {
      if (event.source !== iframeRef.current?.contentWindow) return;
      if (!event.data || event.data.channel !== ZHITU_CHANNEL || event.data.type !== "action") return;
      onAction(event.data);
    };

    window.addEventListener("message", handleMessage);
    return () => {
      window.removeEventListener("message", handleMessage);
    };
  }, [onAction]);

  React.useEffect(() => {
    if (!ready) return;
    postState();
  }, [postState, ready]);

  return (
    <iframe
      ref={iframeRef}
      title="Zhitu Shell"
      srcDoc={html}
      sandbox="allow-scripts allow-same-origin allow-popups allow-forms"
      className={cn("h-full w-full border-0 bg-background", className)}
      onLoad={() => {
        setReady(true);
        postState();
      }}
    />
  );
}
