/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  allowedDevOrigins: ["127.0.0.1"],
  async rewrites() {
    const backendBaseUrl = process.env.BACKEND_BASE_URL || "http://localhost:8081";
    return [
      {
        source: "/backend/:path*",
        destination: `${backendBaseUrl}/:path*`
      }
    ];
  }
};

module.exports = nextConfig;
