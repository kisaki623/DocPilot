"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { getToken } from "@/lib/auth";
import { getQualityConsoleStatus } from "@/lib/quality-api";

export default function QualityNavLink() {
  const pathname = usePathname();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    let cancelled = false;
    const token = getToken();
    if (!token) {
      setVisible(false);
      return;
    }
    getQualityConsoleStatus()
      .then((response) => {
        if (!cancelled) {
          const status = response.data;
          setVisible(Boolean(status?.enabled && status?.authorized));
        }
      })
      .catch(() => {
        if (!cancelled) {
          setVisible(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [pathname]);

  if (!visible) {
    return null;
  }

  return (
    <Link href="/quality?autoload=1" className="dp-shell-link">
      质量
    </Link>
  );
}
