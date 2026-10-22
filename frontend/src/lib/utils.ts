import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
export function cn(...inputs: ClassValue[]) { return twMerge(clsx(inputs)); }
export const money = (value: number, compact = false) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: compact ? 1 : 2, ...(compact ? { notation: "compact" as const } : {}) }).format(value);
export const dateLabel = (value: string) => new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", timeZone: "Asia/Kolkata" }).format(new Date(value + "T12:00:00Z"));
export function addDays(value: string, days: number) { const d = new Date(value + "T12:00:00Z"); d.setUTCDate(d.getUTCDate() + days); return d.toISOString().slice(0, 10); }
export const todayIndia = () => new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
