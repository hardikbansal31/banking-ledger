import { useEffect, useState, useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import {
  RiArrowUpLine,
  RiArrowDownLine,
  RiFileListLine,
  RiRefreshLine,
  RiArrowLeftSLine,
  RiArrowRightSLine,
  RiDownloadLine,
} from "react-icons/ri";
import { useAccountStore } from "../store/accountStore";
import { transactionsApi } from "../api/transactions";
import Card, { CardHeader } from "../components/Card";
import Badge from "../components/Badge";
import { PageSpinner, EmptyState } from "../components/Spinner";
import styles from "./Statement.module.css";

function fmt(amount, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 4,
  }).format(parseFloat(amount) || 0);
}

function fmtDate(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  return (
    d.toLocaleDateString("en-US", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }) +
    " · " +
    d.toLocaleTimeString("en-US", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: true,
    })
  );
}

// ── Ledger entry row ──────────────────────────────────────────────────────────
function EntryRow({ entry }) {
  const isCredit = entry.entryType === "CREDIT";

  return (
    <div className={styles.row}>
      {/* Direction icon */}
      <div
        className={styles.entryIcon}
        style={{
          background: isCredit
            ? "var(--color-credit-bg)"
            : "var(--color-debit-bg)",
          color: isCredit ? "var(--color-credit)" : "var(--color-debit)",
        }}
      >
        {isCredit ? <RiArrowDownLine size={16} /> : <RiArrowUpLine size={16} />}
      </div>

      {/* Description + meta */}
      <div className={styles.rowMain}>
        <p className={styles.rowDesc}>
          {entry.description || (isCredit ? "Credit received" : "Debit sent")}
        </p>
        <p className={styles.rowMeta}>
          <span className="mono" style={{ fontSize: "0.78rem" }}>
            {entry.accountNumber}
          </span>
          <span className={styles.dot}>·</span>
          {fmtDate(entry.createdAt)}
        </p>
      </div>

      {/* Entry type badge */}
      <Badge>{entry.entryType}</Badge>

      {/* Amount */}
      <div className={styles.rowAmounts}>
        <p
          className={`${styles.amount} mono`}
          style={{
            color: isCredit ? "var(--color-credit)" : "var(--color-debit)",
          }}
        >
          {isCredit ? "+" : "-"}
          {fmt(entry.amount, entry.currency)}
        </p>
        <p className={`${styles.balanceAfter} mono`}>
          Balance: {fmt(entry.balanceAfter, entry.currency)}
        </p>
      </div>
    </div>
  );
}

// ── Pagination controls ───────────────────────────────────────────────────────
function Pagination({ page, totalPages, onPage }) {
  if (totalPages <= 1) return null;
  return (
    <div className={styles.pagination}>
      <button
        className={styles.pageBtn}
        onClick={() => onPage(page - 1)}
        disabled={page === 0}
      >
        <RiArrowLeftSLine size={18} />
      </button>

      <span className={styles.pageInfo}>
        Page {page + 1} of {totalPages}
      </span>

      <button
        className={styles.pageBtn}
        onClick={() => onPage(page + 1)}
        disabled={page >= totalPages - 1}
      >
        <RiArrowRightSLine size={18} />
      </button>
    </div>
  );
}

// ── Main Statement page ───────────────────────────────────────────────────────
export default function Statement() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { accounts, fetchAccounts, isLoading: acctLoading } = useAccountStore();

  const [selectedAccount, setSelectedAccount] = useState(
    searchParams.get("account") || "",
  );
  const [entries, setEntries] = useState([]);
  const [pageInfo, setPageInfo] = useState({
    number: 0,
    totalPages: 0,
    totalElements: 0,
  });
  const [loading, setLoading] = useState(false);
  const [currentPage, setCurrentPage] = useState(0);

  // Running totals for selected account
  const [totals, setTotals] = useState({ credits: 0, debits: 0 });

  useEffect(() => {
    fetchAccounts();
  }, []);

  // Auto-select first account if none in URL
  useEffect(() => {
    if (!selectedAccount && accounts.length > 0) {
      setSelectedAccount(accounts[0].accountNumber);
    }
  }, [accounts, selectedAccount]);

  const fetchStatement = useCallback(async (accountNumber, page) => {
    if (!accountNumber) return;
    setLoading(true);
    try {
      const { data } = await transactionsApi.getStatement(
        accountNumber,
        page,
        15,
      );
      setEntries(data.content || []);
      setPageInfo({
        number: data.number || 0,
        totalPages: data.totalPages || 0,
        totalElements: data.totalElements || 0,
      });
      // Compute credit/debit totals from this page
      const credits = (data.content || [])
        .filter((e) => e.entryType === "CREDIT")
        .reduce((s, e) => s + parseFloat(e.amount), 0);
      const debits = (data.content || [])
        .filter((e) => e.entryType === "DEBIT")
        .reduce((s, e) => s + parseFloat(e.amount), 0);
      setTotals({ credits, debits });
    } catch {
      // axios interceptor handles toasts
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedAccount) {
      setCurrentPage(0);
      fetchStatement(selectedAccount, 0);
      setSearchParams({ account: selectedAccount });
    }
  }, [selectedAccount]);

  useEffect(() => {
    if (selectedAccount) {
      fetchStatement(selectedAccount, currentPage);
    }
  }, [currentPage]);

  const account = accounts.find((a) => a.accountNumber === selectedAccount);

  if (acctLoading && accounts.length === 0) return <PageSpinner />;

  return (
    <div className={`${styles.page} fade-in`}>
      <div className={styles.header}>
        <div>
          <h1 className={`${styles.title} display`}>Statement</h1>
          <p className={styles.subtitle}>Ledger entries for your account</p>
        </div>

        {/* Account switcher */}
        <div className={styles.accountPicker}>
          <select
            className={styles.accountSelect}
            value={selectedAccount}
            onChange={(e) => setSelectedAccount(e.target.value)}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.accountNumber}>
                {a.accountNumber} · {a.accountType} ({a.currency})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* ── Account summary bar ───────────────────────────────────── */}
      {account && (
        <div className={styles.summaryBar}>
          <div className={styles.summaryItem}>
            <span className={styles.summaryLabel}>Current balance</span>
            <span className={`${styles.summaryValue} mono`}>
              {fmt(account.balance, account.currency)}
            </span>
          </div>
          <div className={styles.summaryDivider} />
          <div className={styles.summaryItem}>
            <span className={styles.summaryLabel}>Credits this page</span>
            <span
              className={`${styles.summaryValue} mono`}
              style={{ color: "var(--color-credit)" }}
            >
              +{fmt(totals.credits, account.currency)}
            </span>
          </div>
          <div className={styles.summaryDivider} />
          <div className={styles.summaryItem}>
            <span className={styles.summaryLabel}>Debits this page</span>
            <span
              className={`${styles.summaryValue} mono`}
              style={{ color: "var(--color-debit)" }}
            >
              -{fmt(totals.debits, account.currency)}
            </span>
          </div>
          <div className={styles.summaryDivider} />
          <div className={styles.summaryItem}>
            <span className={styles.summaryLabel}>Total entries</span>
            <span className={styles.summaryValue}>
              {pageInfo.totalElements.toLocaleString()}
            </span>
          </div>
        </div>
      )}

      {/* ── Ledger table ─────────────────────────────────────────── */}
      <Card padding="none">
        <CardHeader
          title="Ledger entries"
          subtitle={
            selectedAccount
              ? `${pageInfo.totalElements} entries · newest first`
              : "Select an account"
          }
          action={
            <button
              className={styles.refreshBtn}
              onClick={() => fetchStatement(selectedAccount, currentPage)}
              disabled={loading}
              title="Refresh"
            >
              <RiRefreshLine
                size={16}
                className={loading ? styles.spinning : ""}
              />
            </button>
          }
        />

        {/* Column headers */}
        {entries.length > 0 && (
          <div className={styles.colHeaders}>
            <span>Transaction</span>
            <span>Type</span>
            <span style={{ textAlign: "right" }}>Amount / Balance</span>
          </div>
        )}

        {/* Rows */}
        <div className={styles.entries}>
          {loading ? (
            <div className={styles.loadingRows}>
              {[...Array(6)].map((_, i) => (
                <div key={i} className={`${styles.skeletonRow} skeleton`} />
              ))}
            </div>
          ) : entries.length === 0 ? (
            <EmptyState
              icon={<RiFileListLine />}
              title="No entries yet"
              description="Make a transfer to see your ledger history here."
            />
          ) : (
            entries.map((entry) => <EntryRow key={entry.id} entry={entry} />)
          )}
        </div>

        {/* Pagination */}
        <div className={styles.paginationWrap}>
          <Pagination
            page={currentPage}
            totalPages={pageInfo.totalPages}
            onPage={setCurrentPage}
          />
        </div>
      </Card>
    </div>
  );
}
