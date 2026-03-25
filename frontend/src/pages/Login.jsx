import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { RiEyeLine, RiEyeOffLine, RiWalletLine } from "react-icons/ri";
import { useAuthStore } from "../store/authStore";
import Button from "../components/Button";
import Input from "../components/Input";
import styles from "./Login.module.css";

export default function Login() {
  const [tab, setTab] = useState("login"); // 'login' | 'register'
  const [showPassword, setShowPass] = useState(false);
  const [errors, setErrors] = useState({});
  const { login, register, isLoading, isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  // ── Redirect if already logged in ──────────────────────────────────────────
  useEffect(() => {
    if (isAuthenticated()) navigate("/dashboard", { replace: true });
  }, []);

  // ── Login form ──────────────────────────────────────────────────────────────
  const [loginForm, setLoginForm] = useState({ username: "", password: "" });

  const handleLogin = async (e) => {
    e.preventDefault();
    setErrors({});
    const errs = {};
    if (!loginForm.username) errs.username = "Username is required";
    if (!loginForm.password) errs.password = "Password is required";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    const result = await login(loginForm);
    if (result.success) navigate("/dashboard");
  };

  // ── Register form ────────────────────────────────────────────────────────────
  const [regForm, setRegForm] = useState({
    firstName: "",
    lastName: "",
    username: "",
    email: "",
    password: "",
  });

  const handleRegister = async (e) => {
    e.preventDefault();
    setErrors({});
    const errs = {};
    if (!regForm.firstName) errs.firstName = "Required";
    if (!regForm.lastName) errs.lastName = "Required";
    if (!regForm.username) errs.username = "Required";
    if (!regForm.email) errs.email = "Required";
    if (!regForm.password || regForm.password.length < 8)
      errs.password = "At least 8 characters";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    const result = await register(regForm);
    if (result.success) navigate("/dashboard");
  };

  return (
    <div className={styles.page}>
      {/* ── Left panel — branding ──────────────────────────────────── */}
      <div className={styles.panel}>
        <div className={styles.panelContent}>
          <div className={styles.logo}>
            <RiWalletLine size={28} />
          </div>
          <h1 className={`${styles.headline} display`}>
            Banking built for
            <br />
            the modern age.
          </h1>
          <p className={styles.subline}>
            Instant transfers. Real-time ledger. Complete audit trail.
          </p>
          <div className={styles.features}>
            {[
              "Double-entry accounting",
              "Distributed locking",
              "Tiered fee engine",
              "Scheduled payments",
            ].map((f) => (
              <div key={f} className={styles.feature}>
                <span className={styles.featureDot} />
                {f}
              </div>
            ))}
          </div>
        </div>
        <div className={styles.panelDecor} />
      </div>

      {/* ── Right panel — form ─────────────────────────────────────── */}
      <div className={styles.formSide}>
        <div className={styles.formCard}>
          {/* Tabs */}
          <div className={styles.tabs}>
            <button
              className={`${styles.tab} ${tab === "login" ? styles.tabActive : ""}`}
              onClick={() => {
                setTab("login");
                setErrors({});
              }}
            >
              Sign in
            </button>
            <button
              className={`${styles.tab} ${tab === "register" ? styles.tabActive : ""}`}
              onClick={() => {
                setTab("register");
                setErrors({});
              }}
            >
              Create account
            </button>
          </div>

          {/* ── Login form ─────────────────────────────────────────── */}
          {tab === "login" && (
            <form onSubmit={handleLogin} className={styles.form} noValidate>
              <Input
                label="Username"
                type="text"
                placeholder="your_username"
                value={loginForm.username}
                onChange={(e) =>
                  setLoginForm({ ...loginForm, username: e.target.value })
                }
                error={errors.username}
                autoComplete="username"
                autoFocus
              />
              <Input
                label="Password"
                type={showPassword ? "text" : "password"}
                placeholder="••••••••"
                value={loginForm.password}
                onChange={(e) =>
                  setLoginForm({ ...loginForm, password: e.target.value })
                }
                error={errors.password}
                autoComplete="current-password"
                suffix={
                  <button
                    type="button"
                    className={styles.eyeBtn}
                    onClick={() => setShowPass(!showPassword)}
                    tabIndex={-1}
                  >
                    {showPassword ? <RiEyeOffLine /> : <RiEyeLine />}
                  </button>
                }
              />
              <Button type="submit" fullWidth size="lg" loading={isLoading}>
                Sign in
              </Button>
            </form>
          )}

          {/* ── Register form ──────────────────────────────────────── */}
          {tab === "register" && (
            <form onSubmit={handleRegister} className={styles.form} noValidate>
              <div className={styles.row}>
                <Input
                  label="First name"
                  placeholder="Alice"
                  value={regForm.firstName}
                  onChange={(e) =>
                    setRegForm({ ...regForm, firstName: e.target.value })
                  }
                  error={errors.firstName}
                  required
                />
                <Input
                  label="Last name"
                  placeholder="Smith"
                  value={regForm.lastName}
                  onChange={(e) =>
                    setRegForm({ ...regForm, lastName: e.target.value })
                  }
                  error={errors.lastName}
                  required
                />
              </div>
              <Input
                label="Username"
                placeholder="alice_smith"
                value={regForm.username}
                onChange={(e) =>
                  setRegForm({ ...regForm, username: e.target.value })
                }
                error={errors.username}
                hint="Letters, digits, underscores only"
                required
              />
              <Input
                label="Email"
                type="email"
                placeholder="alice@example.com"
                value={regForm.email}
                onChange={(e) =>
                  setRegForm({ ...regForm, email: e.target.value })
                }
                error={errors.email}
                required
              />
              <Input
                label="Password"
                type={showPassword ? "text" : "password"}
                placeholder="Min. 8 characters"
                value={regForm.password}
                onChange={(e) =>
                  setRegForm({ ...regForm, password: e.target.value })
                }
                error={errors.password}
                hint="Must include uppercase, lowercase, and a digit"
                required
                suffix={
                  <button
                    type="button"
                    className={styles.eyeBtn}
                    onClick={() => setShowPass(!showPassword)}
                    tabIndex={-1}
                  >
                    {showPassword ? <RiEyeOffLine /> : <RiEyeLine />}
                  </button>
                }
              />
              <Button type="submit" fullWidth size="lg" loading={isLoading}>
                Create account
              </Button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
