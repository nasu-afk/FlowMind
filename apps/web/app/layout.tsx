import type { Metadata } from "next";
import "./styles.css";

export const metadata: Metadata = {
  title: "FlowMind — Automation, made observable",
  description: "Build, run, and govern intelligent workflows."
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}

