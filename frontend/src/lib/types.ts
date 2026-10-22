export type Money = number;
export type FlowType = "INFLOW" | "OUTFLOW";
export type FlowStatus = "PLANNED" | "RECEIVED" | "PAID" | "CANCELLED";
export type Source = "SCHEDULED" | "UNPLANNED";
export interface User { id: string; email: string; displayName: string; verified: boolean }
export interface Business { id: string; name: string; currency: string; timezone: string; openingDate: string; openingBalance: Money; reserve: Money; demo: boolean; currentBalance: Money; today: string; revision: number }
export interface Flow { id: string; type: FlowType; amount: Money; description: string; category: string; counterparty: string | null; expectedDate: string; status: FlowStatus; source: Source; recurringRuleId: string | null; occurrenceDate: string | null; updatedAt: string }
export interface Rule { id: string; type: FlowType; amount: Money; description: string; category: string; frequency: "WEEKLY" | "MONTHLY"; startDate: string; endDate: string | null; active: boolean }
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface CashEvent { id: string; date: string; type: FlowType; amount: Money; description: string; origin: string }
export interface Point { date: string; predictedUnplannedNet: Money; scheduledInflows: Money; scheduledOutflows: Money; closingBalance: Money }
export interface Summary { minimumBalance: Money; minimumDate: string; reserveBreachDate: string | null; finalBalance: Money }
export interface Forecast { id: string; startDate: string; endDate: string; openingBalance: Money; reserve: Money; modelName: string; modelVersion: string; generatedAt: string; limitations: string[]; events: CashEvent[]; points: Point[]; summary: Summary; stale: boolean }
export type ChangeKind = "RESCHEDULE" | "AMOUNT_CHANGE" | "ADD_CASH_FLOW" | "REMOVE_CASH_FLOW";
export interface Change { kind: ChangeKind; targetEventId: string | null; newDate: string | null; amount: Money | null; type: FlowType | null; description: string | null }
export interface Scenario { id: string; name: string; description: string; baselineId: string; changes: Change[]; createdAt: string; simulatedAt: string | null }
export interface Comparison { scenario: Scenario; baseline: Forecast; points: Point[]; summary: Summary; deltas: { date: string; difference: Money }[]; minimumBalanceChange: Money; finalBalanceChange: Money; newerBaselineAvailable: boolean; explanation: string }
