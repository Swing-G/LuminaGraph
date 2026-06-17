import { Toaster } from "sonner";

export function Toast() {
  return (
    <Toaster
      position="top-right"
      closeButton
      duration={1250}
      offset={{ right: 4 }}
      toastOptions={{
        classNames: {
          toast: "luminagraph-toast",
          success: "luminagraph-toast-success",
          error: "luminagraph-toast-error",
          info: "luminagraph-toast-info",
          warning: "luminagraph-toast-warning",
          title: "luminagraph-toast-title",
          description: "luminagraph-toast-description",
          closeButton: "luminagraph-toast-close"
        }
      }}
    />
  );
}
