import { z } from "zod";
export const moneyField = z.string().regex(/^-?\d{1,12}(\.\d{1,2})?$/, "Use a valid amount with up to two decimal places.");
export const positiveMoney = moneyField.refine(v => Number(v) > 0, "Amount must be positive.");
export const businessSchema = z.object({
  name: z.string().trim().min(1, "Enter a business name.").max(120),
  timezone: z.string().min(1), openingDate: z.string().min(1, "Choose an opening date."),
  openingBalance: moneyField, reserve: moneyField.refine(v => Number(v) >= 0, "Reserve cannot be negative."), demo: z.boolean(),
});
export const flowSchema = z.object({
  type: z.enum(["INFLOW", "OUTFLOW"]), amount: positiveMoney,
  description: z.string().trim().min(1, "Enter a description.").max(200),
  category: z.string().trim().min(1, "Enter a category.").max(60), counterparty: z.string().max(120),
  expectedDate: z.string().min(1), status: z.enum(["PLANNED", "RECEIVED", "PAID", "CANCELLED"]),
  source: z.enum(["SCHEDULED", "UNPLANNED"]),
});
export type BusinessForm = z.infer<typeof businessSchema>;
export type FlowForm = z.infer<typeof flowSchema>;
