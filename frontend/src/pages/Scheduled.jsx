import { useEffect, useState } from "react";
import toast from "react-hot-toast";
import {
  RiCalendarScheduleLine,
  RiAddLine,
  RiDeleteBinLine,
  RiTimeLine,
  RiCheckLine,
} from "react-icons/ri";
import { useAccountStore } from "../store/accountStore";
import { scheduledApi } from "../api/scheduled";
import Card, { CardHeader } from "../components/Card";
import Button from "../components/Button";
import Badge from "../components/Badge";
import Modal from "../components/Modal";
import Input from "../components/Input";
import { PageSpinner, EmptyState } from "../components/Spinner";
import styles from "./Scheduled.module.css";

function fmt(amount, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(parseFloat(amount) || 0);
}

function fmtDate(iso) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-US", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// ── Cron presets ──────────────────────────────────────────────────────────────
const CRON_PRESETS = [
  { label: "Daily at 9am", value: "0 0 9 * * ?" },
  { label: "Weekly (Mon 9am)", value: "0 0 9 ? * MON" },
  { label: "Monthly (1st at 9am)", value: "0 0 9 1 * ?" },
  { label: "Every minute (testing)", value: "0 * * * * ?" },
  { label: "Custom", value: "custom" },
];

// ── Create form ───────────────────────────────────────────────────────────────
function CreateScheduledForm({ accounts, onSuccess }) {
  const [form, setForm] = useState({
    sourceAccountNumber: "",
    destinationAccountNumber: "",
    amount: "",
    currency: "USD",
    description: "",
    cronPreset: "0 0 9 1 * ?",
    cronExpression: "0 0 9 1 * ?",
  });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);
  const [useCustom, setUseCustom] = useState(false);

  const activeAccounts = accounts.filter((a) => a.status === "ACTIVE");

  const handlePreset = (value) => {
    if (value === "custom") {
      setUseCustom(true);
      setForm((f) => ({ ...f, cronPreset: "custom" }));
    } else {
      setUseCustom(false);
      setForm((f) => ({ ...f, cronPreset: value, cronExpression: value }));
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrors({});
    const errs = {};
    if (!form.sourceAccountNumber) errs.source = "Required";
    if (!form.destinationAccountNumber) errs.dest = "Required";
    if (!form.amount || parseFloat(form.amount) <= 0)
      errs.amount = "Enter a valid amount";
    if (!form.cronExpression) errs.cron = "Required";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    setLoading(true);
    try {
      await scheduledApi.create({
        sourceAccountNumber: form.sourceAccountNumber,
        destinationAccountNumber: form.destinationAccountNumber,
        amount: form.amount,
        currency: form.currency,
        description: form.description,
        cronExpression: form.cronExpression,
      });
      toast.success("Scheduled payment created!");
      onSuccess?.();
    } catch {
      // handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className={styles.createForm} noValidate>
      {/* Source */}
      <div className={styles.formField}>
        <label className={styles.formLabel}>From account</label>
        <select
          className={`${styles.formSelect} ${errors.source ? styles.formSelectError : ""}`}
          value={form.sourceAccountNumber}
          onChange={(e) => {
            const acc = accounts.find(
              (a) => a.accountNumber === e.target.value,
            );
            setForm({
              ...form,
              sourceAccountNumber: e.target.value,
              currency: acc?.currency || "USD",
            });
          }}
        >
          <option value="">Select account</option>
          {activeAccounts
            .filter((a) => a.accountNumber !== form.destinationAccountNumber)
            .map((a) => (
              <option key={a.id} value={a.accountNumber}>
                {a.accountNumber} — {fmt(a.balance, a.currency)}
              </option>
            ))}
        </select>
        {errors.source && (
          <span className={styles.fieldError}>{errors.source}</span>
        )}
      </div>

      {/* Destination */}
      <div className={styles.formField}>
        <label className={styles.formLabel}>To account</label>
        <select
          className={`${styles.formSelect} ${errors.dest ? styles.formSelectError : ""}`}
          value={form.destinationAccountNumber}
          onChange={(e) =>
            setForm({ ...form, destinationAccountNumber: e.target.value })
          }
        >
          <option value="">Select account</option>
          {activeAccounts
            .filter((a) => a.accountNumber !== form.sourceAccountNumber)
            .map((a) => (
              <option key={a.id} value={a.accountNumber}>
                {a.accountNumber} — {a.accountType}
              </option>
            ))}
        </select>
        {errors.dest && (
          <span className={styles.fieldError}>{errors.dest}</span>
        )}
      </div>

      <Input
        label="Amount"
        type="number"
        min="0.01"
        step="0.01"
        placeholder="0.00"
        suffix={form.currency}
        value={form.amount}
        onChange={(e) => setForm({ ...form, amount: e.target.value })}
        error={errors.amount}
        required
      />

      <Input
        label="Description (optional)"
        placeholder="e.g. Monthly rent"
        value={form.description}
        onChange={(e) => setForm({ ...form, description: e.target.value })}
      />

      {/* Schedule */}
      <div className={styles.formField}>
        <label className={styles.formLabel}>Schedule</label>
        <div className={styles.presetGrid}>
          {CRON_PRESETS.map((p) => (
            <button
              key={p.value}
              type="button"
              className={`${styles.presetBtn} ${
                form.cronPreset === p.value ? styles.presetActive : ""
              }`}
              onClick={() => handlePreset(p.value)}
            >
              {p.label}
            </button>
          ))}
        </div>
        {useCustom && (
          <Input
            placeholder="0 0 9 1 * ?"
            value={form.cronExpression}
            onChange={(e) =>
              setForm({ ...form, cronExpression: e.target.value })
            }
            error={errors.cron}
            hint="Format: seconds minutes hours dayOfMonth month dayOfWeek"
          />
        )}
      </div>

      <Button type="submit" fullWidth size="lg" loading={loading}>
        Schedule payment
      </Button>
    </form>
  );
}

// ── Payment card ──────────────────────────────────────────────────────────────
function PaymentCard({ payment, onCancel }) {
  const [cancelling, setCancelling] = useState(false);

  const handleCancel = async () => {
    if (!window.confirm("Cancel this scheduled payment?")) return;
    setCancelling(true);
    try {
      await scheduledApi.cancel(payment.id);
      toast.success("Scheduled payment cancelled.");
      onCancel?.();
    } catch {
      // handled
    } finally {
      setCancelling(false);
    }
  };

  return (
    <Card className={styles.paymentCard}>
      <div className={styles.paymentHeader}>
        <div className={styles.paymentIcon}>
          <RiTimeLine size={18} />
        </div>
        <div className={styles.paymentMeta}>
          <p className={styles.paymentDesc}>
            {payment.description || "Scheduled transfer"}
          </p>
          <p className={styles.paymentAccounts}>
            <span className="mono">{payment.sourceAccountNumber}</span>
            {" → "}
            <span className="mono">{payment.destinationAccountNumber}</span>
          </p>
        </div>
        <Badge>{payment.status}</Badge>
      </div>

      <div className={styles.paymentGrid}>
        <div className={styles.paymentStat}>
          <span className={styles.statLabel}>Amount</span>
          <span className={`${styles.statValue} mono`}>
            {fmt(payment.amount, payment.currency)}
          </span>
        </div>
        <div className={styles.paymentStat}>
          <span className={styles.statLabel}>Schedule</span>
          <span
            className={styles.statValue}
            style={{ fontFamily: "var(--font-mono)", fontSize: "0.8rem" }}
          >
            {payment.cronExpression}
          </span>
        </div>
        <div className={styles.paymentStat}>
          <span className={styles.statLabel}>Next run</span>
          <span className={styles.statValue}>
            {fmtDate(payment.nextFireAt)}
          </span>
        </div>
        <div className={styles.paymentStat}>
          <span className={styles.statLabel}>Executions</span>
          <span className={styles.statValue}>
            {payment.executionCount}
            {payment.lastExecutedAt && (
              <span className={styles.lastRun}>
                {" "}
                · last {fmtDate(payment.lastExecutedAt)}
              </span>
            )}
          </span>
        </div>
      </div>

      {payment.status === "ACTIVE" && (
        <div className={styles.paymentActions}>
          <Button
            variant="danger"
            size="sm"
            icon={<RiDeleteBinLine />}
            loading={cancelling}
            onClick={handleCancel}
          >
            Cancel
          </Button>
        </div>
      )}
    </Card>
  );
}

// ── Main Scheduled page ───────────────────────────────────────────────────────
export default function Scheduled() {
  const { accounts, fetchAccounts, isLoading: acctLoading } = useAccountStore();
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);

  useEffect(() => {
    fetchAccounts();
  }, []);

  const fetchPayments = async () => {
    setLoading(true);
    try {
      const { data } = await scheduledApi.getAll();
      setPayments(data);
    } catch {
      // handled
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPayments();
  }, []);

  if (acctLoading && accounts.length === 0) return <PageSpinner />;

  const active = payments.filter((p) => p.status === "ACTIVE");
  const inactive = payments.filter((p) => p.status !== "ACTIVE");

  return (
    <div className={`${styles.page} fade-in`}>
      <div className={styles.header}>
        <div>
          <h1 className={`${styles.title} display`}>Scheduled Payments</h1>
          <p className={styles.subtitle}>
            {active.length} active · {inactive.length} inactive
          </p>
        </div>
        <Button icon={<RiAddLine />} onClick={() => setShowModal(true)}>
          New schedule
        </Button>
      </div>

      {/* ── Stats strip ───────────────────────────────────────────── */}
      <div className={styles.statsStrip}>
        <div className={styles.statChip}>
          <RiCheckLine size={15} style={{ color: "var(--color-credit)" }} />
          <span>{active.length} active</span>
        </div>
        <div className={styles.statChip}>
          <RiCalendarScheduleLine
            size={15}
            style={{ color: "var(--color-primary)" }}
          />
          <span>
            {payments.reduce((s, p) => s + p.executionCount, 0)} total
            executions
          </span>
        </div>
      </div>

      {/* ── Active payments ───────────────────────────────────────── */}
      {loading ? (
        <PageSpinner />
      ) : payments.length === 0 ? (
        <Card>
          <EmptyState
            icon={<RiCalendarScheduleLine />}
            title="No scheduled payments"
            description="Set up a recurring payment to automate regular transfers."
            action={
              <Button icon={<RiAddLine />} onClick={() => setShowModal(true)}>
                Create schedule
              </Button>
            }
          />
        </Card>
      ) : (
        <>
          {active.length > 0 && (
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>Active</h2>
              <div className={styles.paymentList}>
                {active.map((p) => (
                  <PaymentCard
                    key={p.id}
                    payment={p}
                    onCancel={fetchPayments}
                  />
                ))}
              </div>
            </div>
          )}

          {inactive.length > 0 && (
            <div className={styles.section}>
              <h2 className={styles.sectionTitle}>Inactive / Cancelled</h2>
              <div className={styles.paymentList}>
                {inactive.map((p) => (
                  <PaymentCard
                    key={p.id}
                    payment={p}
                    onCancel={fetchPayments}
                  />
                ))}
              </div>
            </div>
          )}
        </>
      )}

      {/* Create modal */}
      <Modal
        isOpen={showModal}
        onClose={() => setShowModal(false)}
        title="Schedule a recurring payment"
        size="md"
      >
        <CreateScheduledForm
          accounts={accounts}
          onSuccess={() => {
            setShowModal(false);
            fetchPayments();
          }}
        />
      </Modal>
    </div>
  );
}
