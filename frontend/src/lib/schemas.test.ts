import { describe, expect, it } from "vitest";
import { businessSchema, flowSchema, positiveMoney } from "./schemas";
describe("Financial input validation", () => {
  it("accepts decimals and rejects zero, negative, nonfinite, and extra precision", () => {
    expect(positiveMoney.safeParse("123.45").success).toBe(true);
    for (const input of ["0", "-5", "NaN", "1.234", "1e7", "1000000000000"]) expect(positiveMoney.safeParse(input).success).toBe(false);
  });
  it("requires explicit transaction direction and source", () => {
    expect(flowSchema.safeParse({ amount: "10", description: "Sale" }).success).toBe(false);
  });
  it("allows a negative opening balance but not a negative reserve", () => {
    const value = { name: "Studio", timezone: "Asia/Kolkata", openingDate: "2026-10-20", openingBalance: "-100", reserve: "0", demo: false };
    expect(businessSchema.safeParse(value).success).toBe(true);
    expect(businessSchema.safeParse({ ...value, reserve: "-1" }).success).toBe(false);
  });
});
