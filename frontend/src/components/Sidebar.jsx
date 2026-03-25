import { NavLink, useNavigate } from "react-router-dom";
import {
  RiDashboardLine,
  RiBankLine,
  RiExchangeLine,
  RiCalendarScheduleLine,
  RiFileListLine,
  RiLogoutBoxLine,
  RiShieldUserLine,
  RiWalletLine,
} from "react-icons/ri";
import { useAuthStore } from "../store/authStore";
import styles from "./Sidebar.module.css";

const NAV_ITEMS = [
  { to: "/dashboard", icon: <RiDashboardLine />, label: "Dashboard" },
  { to: "/accounts", icon: <RiBankLine />, label: "Accounts" },
  { to: "/transfer", icon: <RiExchangeLine />, label: "Transfer" },
  { to: "/statement", icon: <RiFileListLine />, label: "Statement" },
  { to: "/scheduled", icon: <RiCalendarScheduleLine />, label: "Scheduled" },
];

export default function Sidebar() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const initials = user
    ? `${user.firstName[0]}${user.lastName[0]}`.toUpperCase()
    : "??";

  return (
    <aside className={styles.sidebar}>
      {/* ── Brand ───────────────────────────────────────────────────── */}
      <div className={styles.brand}>
        <div className={styles.brandIcon}>
          <RiWalletLine size={20} />
        </div>
        <span className={styles.brandName}>
          {import.meta.env.VITE_APP_NAME || "BankLedger"}
        </span>
      </div>

      {/* ── Navigation ──────────────────────────────────────────────── */}
      <nav className={styles.nav}>
        <p className={styles.navSection}>Menu</p>
        {NAV_ITEMS.map(({ to, icon, label }) => (
          <NavLink
            key={to}
            to={to}
            className={({ isActive }) =>
              `${styles.navItem} ${isActive ? styles.navItemActive : ""}`
            }
          >
            <span className={styles.navIcon}>{icon}</span>
            <span className={styles.navLabel}>{label}</span>
          </NavLink>
        ))}
      </nav>

      {/* ── Bottom — user + logout ──────────────────────────────────── */}
      <div className={styles.bottom}>
        <div className={styles.userCard}>
          <div className={styles.avatar}>{initials}</div>
          <div className={styles.userInfo}>
            <p className={styles.userName}>
              {user?.firstName} {user?.lastName}
            </p>
            <p className={styles.userRole}>
              {user?.role === "ADMIN" ? (
                <span className={styles.adminBadge}>
                  <RiShieldUserLine size={11} /> Admin
                </span>
              ) : (
                "Member"
              )}
            </p>
          </div>
        </div>
        <button className={styles.logoutBtn} onClick={handleLogout}>
          <RiLogoutBoxLine size={18} />
          <span>Log out</span>
        </button>
      </div>
    </aside>
  );
}
