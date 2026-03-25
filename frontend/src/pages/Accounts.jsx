import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  RiAddLine,
  RiBankLine,
  RiArrowRightLine,
  RiInformationLine,
} from "react-icons/ri";
import { useAccountStore } from "../store/accountStore";
import { useAuthStore } from "../store/authStore";
import Card, { CardHeader } from "../components/Card";
import Button from "../components/Button";
import Badge from "../components/Badge";
import Modal from "../components/Modal";
import { PageSpinner, EmptyState } from "../components/Spinner";
import OpenAccountForm from "../components/OpenAccountForm";
import styles from "./Accounts.module.css";

function fmt(amount, currency = "USD") {
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(parseFloat(amount) || 0);
}

export default function Accounts() {
  const { accounts, fetchAccounts, isLoading } = useAccountStore();
  const { isAdmin } = useAuthStore();
  const [openModal, setOpenModal] = useState(false);
  const [selected, setSelected] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    fetchAccounts(true);
  }, []);

  if (isLoading && accounts.length === 0) return <PageSpinner />;

  return (
    <div className={`${styles.page} fade-in`}>
      <div className={styles.header}>
        <div>
          <h1 className={`${styles.title} display`}>Accounts</h1>
          <p className={styles.subtitle}>
            {accounts.length} account{accounts.length !== 1 ? "s" : ""} total
          </p>
        </div>
        <Button icon={<RiAddLine />} onClick={() => setOpenModal(true)}>
          New account
        </Button>
      </div>

      {accounts.length === 0 ? (
        <Card>
          <EmptyState
            icon={<RiBankLine />}
            title="No accounts"
            description="Open your first account to get started."
            action={
              <Button icon={<RiAddLine />} onClick={() => setOpenModal(true)}>
                Open account
              </Button>
            }
          />
        </Card>
      ) : (
        <div className={styles.list}>
          {accounts.map((account) => (
            <Card key={account.id} padding="none" className={styles.accountRow}>
              {/* Left — icon + info */}
              <div className={styles.rowLeft}>
                <div className={styles.iconWrap}>
                  <RiBankLine size={20} />
                </div>
                <div className={styles.info}>
                  <div className={styles.infoTop}>
                    <span className={`${styles.accountNum} mono`}>
                      {account.accountNumber}
                    </span>
                    <Badge>{account.status}</Badge>
                  </div>
                  <p className={styles.accountType}>
                    {account.accountType} · {account.currency}
                  </p>
                </div>
              </div>

              {/* Center — balance */}
              <div className={styles.balanceWrap}>
                <p className={styles.balanceLabel}>Balance</p>
                <p className={`${styles.balance} mono`}>
                  {fmt(account.balance, account.currency)}
                </p>
              </div>

              {/* Right — actions */}
              <div className={styles.rowActions}>
                <Button
                  variant="secondary"
                  size="sm"
                  icon={<RiArrowRightLine />}
                  onClick={() =>
                    navigate(`/statement?account=${account.accountNumber}`)
                  }
                >
                  Statement
                </Button>
                <button
                  className={styles.infoBtn}
                  onClick={() => setSelected(account)}
                  title="Account details"
                >
                  <RiInformationLine size={18} />
                </button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Open account modal */}
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

      {/* Account detail modal */}
      <Modal
        isOpen={!!selected}
        onClose={() => setSelected(null)}
        title="Account details"
        size="sm"
      >
        {selected && (
          <div className={styles.detailGrid}>
            {[
              [
                "Account number",
                <span className="mono">{selected.accountNumber}</span>,
              ],
              ["Type", selected.accountType],
              ["Currency", selected.currency],
              ["Status", <Badge>{selected.status}</Badge>],
              [
                "Balance",
                <span className="mono">
                  {fmt(selected.balance, selected.currency)}
                </span>,
              ],
              [
                "Min balance",
                <span className="mono">
                  {fmt(selected.minimumBalance, selected.currency)}
                </span>,
              ],
              ["Owner", selected.ownerUsername],
              ["Opened", new Date(selected.createdAt).toLocaleDateString()],
            ].map(([label, value]) => (
              <div key={label} className={styles.detailRow}>
                <span className={styles.detailLabel}>{label}</span>
                <span className={styles.detailValue}>{value}</span>
              </div>
            ))}
            <div className={styles.detailActions}>
              <Button
                fullWidth
                icon={<RiArrowRightLine />}
                onClick={() => {
                  setSelected(null);
                  navigate(`/statement?account=${selected.accountNumber}`);
                }}
              >
                View statement
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
