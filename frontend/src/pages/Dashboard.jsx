import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  RiAddLine,
  RiBankLine,
  RiArrowRightLine,
  RiExchangeLine,
  RiShieldCheckLine,
  RiTimeLine,
  RiMoneyDollarCircleLine,
} from "react-icons/ri";
import { useAuthStore } from "../store/authStore";
import { useAccountStore } from "../store/accountStore";
import { transactionsApi } from "../api/transactions";
import Card, { CardHeader } from "../components/Card";
import Button from "../components/Button";
import Badge from "../components/Badge";
import Modal from "../components/Modal";
import { PageSpinner, EmptyState } from "../components/Spinner";
import OpenAccountForm from "../components/OpenAccountForm";
import styles from "./Dashboard.module.css";

// ── Helpers ────────────────────────────────────────────────────────────────────
function fmt(amount, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(parseFloat(amount) || 0);
}

function timeAgo(isoString) {
  if (!isoString) return "—";
  const diff = Date.now() - new Date(isoString).getTime();
  const mins = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  if (hours < 24) return `${hours}h ago`;
  return `${days}d ago`;
}

// ── Account Card ──────────────────────────────────────────────────────────────
function AccountCard({ account, onClick }) {
  const typeColors = {
    CHECKING: "#3B82F6",
    SAVINGS: "#8B5CF6",
    INVESTMENT: "#10B981",
    INTERNAL: "#6B7280",
  };
  const color = typeColors[account.accountType] || "#6B7280";

  return (
    <div className={styles.accountCard} onClick={onClick}>
      <div className={styles.accountCardTop}>
        <div
          className={styles.accountTypeIcon}
          style={{ background: color + "18", color }}
        >
          <RiBankLine size={18} />
        </div>
        <Badge>{account.status}</Badge>
      </div>

      <div className={styles.accountBalance}>
        {fmt(account.balance, account.currency)}
      </div>

      <div className={styles.accountMeta}>
        <span className={styles.accountNumber}>{account.accountNumber}</span>
        <span className={styles.accountType}>{account.accountType}</span>
      </div>

      <div className={styles.accountFooter}>
        <span className={styles.currency}>{account.currency}</span>
        <RiArrowRightLine size={16} className={styles.arrow} />
      </div>
    </div>
  );
}

// ── Recent Transaction Row ────────────────────────────────────────────────────
function TxRow({ tx, myAccounts }) {
  const myAccountNumbers = myAccounts.map((a) => a.accountNumber);
  const isCredit =
    myAccountNumbers.includes(tx.destinationAccountNumber) &&
    !myAccountNumbers.includes(tx.sourceAccountNumber);
  const isDebit = myAccountNumbers.includes(tx.sourceAccountNumber);

  const sign = isCredit ? "+" : isDebit ? "-" : "";
  const color = isCredit
    ? "var(--color-credit)"
    : isDebit
      ? "var(--color-debit)"
      : "var(--color-text-primary)";

  return (
    <div className={styles.txRow}>
      <div
        className={styles.txIcon}
        style={{
          background: isCredit
            ? "var(--color-credit-bg)"
            : "var(--color-debit-bg)",
          color: isCredit ? "var(--color-credit)" : "var(--color-debit)",
        }}
      >
        <RiExchangeLine size={16} />
      </div>
      <div className={styles.txInfo}>
        <p className={styles.txDesc}>
          {tx.description ||
            (isCredit
              ? `From ${tx.sourceAccountNumber}`
              : `To ${tx.destinationAccountNumber}`)}
        </p>
        <p className={styles.txMeta}>
          {timeAgo(tx.createdAt)} · <Badge>{tx.status}</Badge>
        </p>
      </div>
      <span className={styles.txAmount} style={{ color }}>
        {sign}
        {fmt(tx.amount, tx.currency)}
      </span>
    </div>
  );
}

// ── Main Dashboard ────────────────────────────────────────────────────────────
export default function Dashboard() {
  const { user } = useAuthStore();
  const { accounts, fetchAccounts, isLoading } = useAccountStore();
  const [recentTx, setRecentTx] = useState([]);
  const [txLoading, setTxLoading] = useState(false);
  const [openModal, setOpenModal] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    fetchAccounts(true);
  }, []);

  // Fetch recent transactions across all accounts
  useEffect(() => {
    if (accounts.length === 0) return;
    const firstAccount = accounts[0];
    setTxLoading(true);
    transactionsApi
      .getByAccount(firstAccount.accountNumber, 0, 5)
      .then(({ data }) => setRecentTx(data.content || []))
      .catch(() => {})
      .finally(() => setTxLoading(false));
  }, [accounts]);

  // Totals per currency
  const totals = accounts
    .filter((a) => a.status === "ACTIVE")
    .reduce((acc, a) => {
      acc[a.currency] = (acc[a.currency] || 0) + parseFloat(a.balance);
      return acc;
    }, {});

  if (isLoading && accounts.length === 0) return <PageSpinner />;

  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";

  return (
    <div className={`${styles.page} fade-in`}>
      {/* ── Header ─────────────────────────────────────────────────── */}
      <div className={styles.header}>
        <div>
          <p className={styles.greeting}>{greeting},</p>
          <h1 className={`${styles.name} display`}>
            {user?.firstName} {user?.lastName}
          </h1>
        </div>
        <Button icon={<RiAddLine />} onClick={() => setOpenModal(true)}>
          New account
        </Button>
      </div>

      {/* ── Summary strip ──────────────────────────────────────────── */}
      {Object.keys(totals).length > 0 && (
        <div className={styles.summaryStrip}>
          {Object.entries(totals).map(([currency, total]) => (
            <div key={currency} className={styles.summaryItem}>
              <RiMoneyDollarCircleLine
                size={18}
                className={styles.summaryIcon}
              />
              <div>
                <p className={styles.summaryLabel}>Total {currency} balance</p>
                <p className={styles.summaryValue}>{fmt(total, currency)}</p>
              </div>
            </div>
          ))}
          <div className={styles.summaryItem}>
            <RiBankLine size={18} className={styles.summaryIcon} />
            <div>
              <p className={styles.summaryLabel}>Active accounts</p>
              <p className={styles.summaryValue}>
                {accounts.filter((a) => a.status === "ACTIVE").length}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ── Account grid ───────────────────────────────────────────── */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h2 className={styles.sectionTitle}>Your accounts</h2>
          <button
            className={styles.seeAll}
            onClick={() => navigate("/accounts")}
          >
            See all <RiArrowRightLine size={14} />
          </button>
        </div>

        {accounts.length === 0 ? (
          <Card>
            <EmptyState
              icon={<RiBankLine />}
              title="No accounts yet"
              description="Open your first account to start sending and receiving money."
              action={
                <Button icon={<RiAddLine />} onClick={() => setOpenModal(true)}>
                  Open account
                </Button>
              }
            />
          </Card>
        ) : (
          <div className={styles.accountGrid}>
            {accounts.map((account) => (
              <AccountCard
                key={account.id}
                account={account}
                onClick={() =>
                  navigate(`/statement?account=${account.accountNumber}`)
                }
              />
            ))}
            {/* Add account tile */}
            <div
              className={`${styles.accountCard} ${styles.addCard}`}
              onClick={() => setOpenModal(true)}
            >
              <RiAddLine size={28} />
              <span>New account</span>
            </div>
          </div>
        )}
      </div>

      {/* ── Quick actions ───────────────────────────────────────────── */}
      <div className={styles.quickActions}>
        <Card
          className={styles.quickAction}
          onClick={() => navigate("/transfer")}
          padding="md"
        >
          <div
            className={styles.qaIcon}
            style={{
              background: "var(--color-primary-light)",
              color: "var(--color-primary-dark)",
            }}
          >
            <RiExchangeLine size={22} />
          </div>
          <div>
            <p className={styles.qaTitle}>Transfer</p>
            <p className={styles.qaDesc}>Send money between accounts</p>
          </div>
        </Card>
        <Card
          className={styles.quickAction}
          onClick={() => navigate("/statement")}
          padding="md"
        >
          <div
            className={styles.qaIcon}
            style={{
              background: "var(--color-credit-bg)",
              color: "var(--color-credit)",
            }}
          >
            <RiShieldCheckLine size={22} />
          </div>
          <div>
            <p className={styles.qaTitle}>Statement</p>
            <p className={styles.qaDesc}>View full ledger history</p>
          </div>
        </Card>
        <Card
          className={styles.quickAction}
          onClick={() => navigate("/scheduled")}
          padding="md"
        >
          <div
            className={styles.qaIcon}
            style={{
              background: "var(--color-authorized-bg)",
              color: "var(--color-authorized)",
            }}
          >
            <RiTimeLine size={22} />
          </div>
          <div>
            <p className={styles.qaTitle}>Scheduled</p>
            <p className={styles.qaDesc}>Manage recurring payments</p>
          </div>
        </Card>
      </div>

      {/* ── Recent transactions ─────────────────────────────────────── */}
      {accounts.length > 0 && (
        <div className={styles.section}>
          <Card padding="none">
            <CardHeader
              title="Recent transactions"
              subtitle={`Activity on ${accounts[0]?.accountNumber}`}
              action={
                <button
                  className={styles.seeAll}
                  onClick={() =>
                    navigate(`/statement?account=${accounts[0]?.accountNumber}`)
                  }
                >
                  Full statement <RiArrowRightLine size={14} />
                </button>
              }
            />
            <div style={{ padding: "0 24px 16px" }}>
              {txLoading ? (
                <div style={{ padding: "24px 0", textAlign: "center" }}>
                  Loading...
                </div>
              ) : recentTx.length === 0 ? (
                <EmptyState
                  icon={<RiExchangeLine />}
                  title="No transactions yet"
                  description="Make your first transfer to see activity here."
                />
              ) : (
                recentTx.map((tx) => (
                  <TxRow key={tx.id} tx={tx} myAccounts={accounts} />
                ))
              )}
            </div>
          </Card>
        </div>
      )}

      {/* ── Open account modal ─────────────────────────────────────── */}
      <Modal
        isOpen={openModal}
        onClose={() => setOpenModal(false)}
        title="Open a new account"
      >
        <OpenAccountForm
          onSuccess={() => {
            setOpenModal(false);
            fetchAccounts(true);
          }}
        />
      </Modal>
    </div>
  );
}
