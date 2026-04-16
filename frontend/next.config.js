/** @type {import('next').NextConfig} */
const nextConfig = {
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
