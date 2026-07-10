package com.colla.platform.shared.request;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestBoundaryContext {
    private static final ThreadLocal<RequestBoundary> CURRENT = new ThreadLocal<>();

    private RequestBoundaryContext() {
    }

    public static void bind(HttpServletRequest request) {
        CURRENT.set(RequestBoundary.from(request));
    }

    public static RequestBoundary current() {
        RequestBoundary boundary = CURRENT.get();
        return boundary == null ? RequestBoundary.systemTask() : boundary;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public record RequestBoundary(String sourceUi, String apiSurface, String client, String requestPath) {
        static RequestBoundary from(HttpServletRequest request) {
            String path = request.getRequestURI();
            String client = clean(request.getHeader("X-Colla-Client"));
            String explicitUi = normalizeSourceUi(request.getHeader("X-Colla-Ui"));
            String sourceUi = explicitUi == null ? inferSourceUi(path, client) : explicitUi;
            return new RequestBoundary(sourceUi, inferApiSurface(path), client == null ? "api" : client, path);
        }

        static RequestBoundary systemTask() {
            return new RequestBoundary("system_task", "system", "system", "");
        }

        private static String inferSourceUi(String path, String client) {
            if (path != null && path.startsWith("/api/admin/")) {
                return "admin_console";
            }
            if ("web".equals(client)) {
                return "user_workspace";
            }
            return "api_call";
        }

        private static String inferApiSurface(String path) {
            if (path == null || path.isBlank()) {
                return "system";
            }
            if (path.startsWith("/api/admin/")) {
                return "admin_governance";
            }
            if (path.startsWith("/api/platform/") || path.startsWith("/api/resource-permissions/") || path.startsWith("/api/files/")) {
                return "shared_platform";
            }
            if (path.startsWith("/api/")) {
                return "user_collaboration";
            }
            return "system";
        }

        private static String normalizeSourceUi(String value) {
            String clean = clean(value);
            if (clean == null) {
                return null;
            }
            return switch (clean) {
                case "admin", "admin_console", "management" -> "admin_console";
                case "user", "user_workspace", "workspace" -> "user_workspace";
                case "system", "system_task" -> "system_task";
                case "migration", "migration_script" -> "migration_script";
                default -> "api_call";
            };
        }

        private static String clean(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.trim().toLowerCase().replace('-', '_');
        }
    }
}
