import { useEffect, useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import {
  RiExchangeLine,
  RiArrowRightLine,
  RiInformationLine,
  RiCheckboxCircleLine,
  RiShieldCheckLine,
} from "react-icons/ri";
import { useAccountStore } from "../store/accountStore";
import { transactionsApi } from "../api/transactions";
import Input from "../components/Input";
import Button from "../components/Button";
import Card, { CardHeader } from "../components/Card";
import Badge from "../components/Badge";
import { PageSpinner } from "../components/Spinner";
import styles from "./Transfer.module.css";

// Fee tier rates — mirror backend config
const FEE_TIERS = [
  { threshold: 0, max: 1000, rate: 0.015, label: "Standard (1.50%)" },
  { threshold: 1000, max: 10000, rate: 0.01, label: "Preferred (1.00%)" },
  { threshold: 10000, max: Infinity, rate: 0.005, label: "Premium (0.50%)" },
];

function calculateFee(amount) {
  const n = parseFloat(amount);
  if (!n || n <= 0) return { fee: 0, rate: 0, tier: null };
  const tier =
    FEE_TIERS.find((t) => n >= t.threshold && n < t.max) || FEE_TIERS[2];
  return {
    fee: parseFloat((n * tier.rate).toFixed(2)),
    rate: tier.rate,
    tier: tier.label,
  };
}

function fmt(n, currency = "USD") {
  if (!n && n !== 0) return "—";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(n);
}

// ── Account selector dropdown ──────────────────────────────────────────────
function AccountSelect({ label, value, onChange, accounts, exclude, error }) {
  const available = accounts.filter(
    (a) => a.status === "ACTIVE" && a.accountNumber !== exclude,
  );
  return (
    <div className={styles.selectWrap}>
      <label className={styles.selectLabel}>{label}</label>
      <div
        className={`${styles.selectBox} ${error ? styles.selectError : ""} ${value ? styles.selectFilled : ""}`}
      >
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={styles.select}
        >
          <option value="">Select account</option>
          {available.map((a) => (
            <option key={a.id} value={a.accountNumber}>
              {a.accountNumber} — {a.accountType} ({fmt(a.balance, a.currency)})
            </option>
          ))}
        </select>
      </div>
      {error && <span className={styles.selectErrorMsg}>{error}</span>}
    </div>
  );
}

// ── Confirmation screen ────────────────────────────────────────────────────
function ConfirmationScreen({
  form,
  fee,
  accounts,
  onConfirm,
  onBack,
  loading,
}) {
  const src = accounts.find((a) => a.accountNumber === form.source);
  const dest = accounts.find((a) => a.accountNumber === form.destination);
  const amt = parseFloat(form.amount);

  return (
    <div className={styles.confirmation}>
      <div className={styles.confirmIcon}>
        <RiShieldCheckLine size={32} />
      </div>
      <h2 className={styles.confirmTitle}>Review transfer</h2>
      <p className={styles.confirmSubtitle}>
        Please review the details before confirming.
      </p>

      <div className={styles.confirmDetails}>
        <div className={styles.confirmFlow}>
          <div className={styles.confirmAccount}>
            <p className={`${styles.confirmAccountNum} mono`}>{form.source}</p>
            <p className={styles.confirmAccountType}>{src?.accountType}</p>
          </div>
          <div className={styles.confirmArrow}>
            <RiArrowRightLine size={20} />
          </div>
          <div className={styles.confirmAccount}>
            <p className={`${styles.confirmAccountNum} mono`}>
              {form.destination}
            </p>
            <p className={styles.confirmAccountType}>{dest?.accountType}</p>
          </div>
        </div>

        {[
          ["Transfer amount", fmt(amt, form.currency)],
          ["Platform fee", fmt(fee.fee, form.currency)],
          ["Total debit", fmt(amt + fee.fee, form.currency)],
          ["Fee tier", fee.tier],
          ["Description", form.description || "—"],
        ].map(([label, val]) => (
          <div key={label} className={styles.confirmRow}>
            <span className={styles.confirmLabel}>{label}</span>
            <span
              className={`${styles.confirmValue} ${label === "Total debit" ? styles.confirmTotal : ""}`}
            >
              {val}
            </span>
          </div>
        ))}
      </div>

      <div className={styles.confirmActions}>
        <Button variant="secondary" onClick={onBack} disabled={loading}>
          Back
        </Button>
        <Button
          onClick={onConfirm}
          loading={loading}
          icon={<RiCheckboxCircleLine />}
        >
          Confirm transfer
        </Button>
      </div>
    </div>
  );
}

// ── Success screen ─────────────────────────────────────────────────────────
function SuccessScreen({ result, onDone }) {
  return (
    <div className={styles.success}>
      <div className={styles.successIcon}>
        <RiCheckboxCircleLine size={40} />
      </div>
      <h2 className={styles.successTitle}>Transfer complete</h2>
      <p className={styles.successSub}>
        {fmt(result.amount, result.currency)} sent successfully
      </p>
      <div className={styles.successMeta}>
        <span
          className="mono"
          style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)" }}
        >
          ID: {result.id}
        </span>
        <Badge>{result.status}</Badge>
      </div>
      <div className={styles.successActions}>
        <Button onClick={onDone}>Back to dashboard</Button>
      </div>
    </div>
  );
}

// ── Main Transfer page ─────────────────────────────────────────────────────
export default function Transfer() {
  const { accounts, fetchAccounts, refreshAccount, isLoading } =
    useAccountStore();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    source: "",
    destination: "",
    amount: "",
    currency: "USD",
    description: "",
  });
  const [errors, setErrors] = useState({});
  const [step, setStep] = useState("form"); // 'form' | 'confirm' | 'success'
  const [result, setResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    fetchAccounts();
  }, []);

  // Auto-set currency when source account changes
  useEffect(() => {
    if (form.source) {
      const src = accounts.find((a) => a.accountNumber === form.source);
      if (src) setForm((f) => ({ ...f, currency: src.currency }));
    }
  }, [form.source, accounts]);

  const fee = calculateFee(form.amount);
  const totalDebit = (parseFloat(form.amount) || 0) + fee.fee;
  const srcAccount = accounts.find((a) => a.accountNumber === form.source);
  const insufficientFunds =
    srcAccount && totalDebit > parseFloat(srcAccount.balance);

  const validate = useCallback(() => {
    const errs = {};
    if (!form.source) errs.source = "Select source account";
    if (!form.destination) errs.destination = "Select destination account";
    if (!form.amount || parseFloat(form.amount) <= 0)
      errs.amount = "Enter a valid amount";
    if (insufficientFunds)
      errs.amount = `Insufficient funds (need ${fmt(totalDebit, form.currency)}, have ${fmt(srcAccount?.balance, form.currency)})`;
    return errs;
  }, [form, insufficientFunds, totalDebit, srcAccount]);

  const handleReview = (e) => {
    e.preventDefault();
    const errs = validate();
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }
    setErrors({});
    setStep("confirm");
  };

  const handleConfirm = async () => {
    setSubmitting(true);
    try {
      const { data } = await transactionsApi.transfer({
        sourceAccountNumber: form.source,
        destinationAccountNumber: form.destination,
        amount: form.amount,
        currency: form.currency,
        description: form.description,
      });
      setResult(data);
      setStep("success");
      // Refresh balances for both accounts
      refreshAccount(form.source);
      refreshAccount(form.destination);
      toast.success("Transfer settled successfully!");
    } catch (err) {
      // 422/409/500 already handled by axios interceptor
      // Put user back on form if it's a fixable error
      if (err.response?.status === 422 || err.response?.status === 409) {
        setStep("form");
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (isLoading && accounts.length === 0) return <PageSpinner />;

  return (
    <div className={`${styles.page} fade-in`}>
      <div className={styles.header}>
        <h1 className={`${styles.title} display`}>Transfer</h1>
        <p className={styles.subtitle}>Move money between your accounts</p>
      </div>

      <div className={styles.layout}>
        {/* ── Main form / steps ─────────────────────────────────── */}
        <div className={styles.main}>
          <Card>
            {step === "form" && (
              <form onSubmit={handleReview} className={styles.form} noValidate>
                <AccountSelect
                  label="From account"
                  value={form.source}
                  onChange={(v) => setForm({ ...form, source: v })}
                  accounts={accounts}
                  exclude={form.destination}
                  error={errors.source}
                />

                <div className={styles.exchangeIcon}>
                  <RiArrowRightLine size={20} />
                </div>

                <AccountSelect
                  label="To account"
                  value={form.destination}
                  onChange={(v) => setForm({ ...form, destination: v })}
                  accounts={accounts}
                  exclude={form.source}
                  error={errors.destination}
                />

                <div className={styles.divider} />

                <Input
                  label="Amount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="0.00"
                  value={form.amount}
                  onChange={(e) => setForm({ ...form, amount: e.target.value })}
                  error={errors.amount}
                  suffix={form.currency}
                  required
                />

                <Input
                  label="Description (optional)"
                  type="text"
                  placeholder="e.g. Rent payment, Invoice #42"
                  value={form.description}
                  onChange={(e) =>
                    setForm({ ...form, description: e.target.value })
                  }
                  maxLength={255}
                />

                <Button
                  type="submit"
                  fullWidth
                  size="lg"
                  icon={<RiArrowRightLine />}
                  disabled={accounts.length < 2}
                >
                  Review transfer
                </Button>

                {accounts.length < 2 && (
                  <p className={styles.noAccounts}>
                    You need at least 2 active accounts to make a transfer.
                  </p>
                )}
              </form>
            )}

            {step === "confirm" && (
              <ConfirmationScreen
                form={form}
                fee={fee}
                accounts={accounts}
                onConfirm={handleConfirm}
                onBack={() => setStep("form")}
                loading={submitting}
              />
            )}

            {step === "success" && (
              <SuccessScreen
                result={result}
                onDone={() => navigate("/dashboard")}
              />
            )}
          </Card>
        </div>

        {/* ── Fee info panel ──────────────────────────────────────── */}
        {step === "form" && (
          <div className={styles.sidebar}>
            <Card>
              <CardHeader title="Fee preview" />
              <div className={styles.feeBreakdown}>
                <div className={styles.feeRow}>
                  <span className={styles.feeLabel}>Transfer amount</span>
                  <span className={`${styles.feeValue} mono`}>
                    {form.amount
                      ? fmt(parseFloat(form.amount), form.currency)
                      : "—"}
                  </span>
                </div>
                <div className={styles.feeRow}>
                  <span className={styles.feeLabel}>
                    Platform fee
                    {fee.tier && (
                      <span className={styles.feeTier}>{fee.tier}</span>
                    )}
                  </span>
                  <span
                    className={`${styles.feeValue} mono`}
                    style={{ color: "var(--color-debit)" }}
                  >
                    {fee.fee > 0 ? fmt(fee.fee, form.currency) : "—"}
                  </span>
                </div>
                <div className={`${styles.feeRow} ${styles.feeTotal}`}>
                  <span className={styles.feeLabel}>Total debit</span>
                  <span className={`${styles.feeValue} mono`}>
                    {form.amount ? fmt(totalDebit, form.currency) : "—"}
                  </span>
                </div>
              </div>

              {/* Fee tier table */}
              <div className={styles.tierTable}>
                <p className={styles.tierTitle}>
                  <RiInformationLine size={14} /> Fee tiers
                </p>
                {FEE_TIERS.map((t, i) => {
                  const isActive = fee.tier === t.label;
                  return (
                    <div
                      key={i}
                      className={`${styles.tierRow} ${isActive ? styles.tierActive : ""}`}
                    >
                      <span className={styles.tierRange}>
                        {t.max === Infinity
                          ? `≥ $${t.threshold.toLocaleString()}`
                          : `$${t.threshold.toLocaleString()} – $${t.max.toLocaleString()}`}
                      </span>
                      <span className={styles.tierRate}>
                        {(t.rate * 100).toFixed(2)}%
                      </span>
                    </div>
                  );
                })}
              </div>
            </Card>

            {/* Source balance card */}
            {srcAccount && (
              <Card>
                <p className={styles.balanceLabel}>Available balance</p>
                <p className={`${styles.balanceAmt} mono`}>
                  {fmt(srcAccount.balance, srcAccount.currency)}
                </p>
                <p className={styles.balanceAccount}>
                  {srcAccount.accountNumber}
                </p>
                {insufficientFunds && (
                  <p className={styles.insufficient}>
                    Insufficient funds for this transfer
                  </p>
                )}
              </Card>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
