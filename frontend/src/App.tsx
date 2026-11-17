import { useEffect, useState } from "react";
import { setLanguage } from "./api";
import { t, type Lang } from "./i18n";
import { CheckerPage } from "./pages/CheckerPage";
import { NewRequestPage } from "./pages/NewRequestPage";
import { RequestDetailPage } from "./pages/RequestDetailPage";
import { RequestsPage } from "./pages/RequestsPage";

/** Hash routes: #/new, #/requests, #/requests/<id>, #/check. No router library needed for four screens. */
function useHashRoute(): [string, (path: string) => void] {
  const read = () => window.location.hash.replace(/^#/, "") || "/new";
  const [path, setPath] = useState(read);
  useEffect(() => {
    const onChange = () => {
      setPath(read());
      try {
        window.scrollTo(0, 0);
      } catch {
        /* not available (tests) */
      }
    };
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return [path, (p: string) => { window.location.hash = p; }];
}

export default function App() {
  const [lang, setLang] = useState<Lang>(() => {
    try {
      return (localStorage.getItem("lang") as Lang) || "ar";
    } catch {
      return "ar";
    }
  });
  const s = t(lang);
  const [path, navigate] = useHashRoute();
  setLanguage(lang);

  useEffect(() => {
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === "ar" ? "rtl" : "ltr";
    try {
      localStorage.setItem("lang", lang);
    } catch {
      /* storage unavailable */
    }
  }, [lang]);

  const detail = path.match(/^\/requests\/([0-9a-f-]{36})$/);
  const nav = [
    { path: "/new", label: s.navNew },
    { path: "/requests", label: s.navRequests },
    { path: "/check", label: s.navCheck },
  ];

  return (
    <div className="page">
      <header>
        <div className="brand">
          <span className="logo">J</span> Justify AI
        </div>
        <nav>
          {nav.map((n) => (
            <a key={n.path} href={`#${n.path}`} className={path.startsWith(n.path) ? "active" : undefined}>
              {n.label}
            </a>
          ))}
        </nav>
        <button className="link" onClick={() => setLang(lang === "ar" ? "en" : "ar")}>{s.switchLang}</button>
      </header>

      <main>
        {detail ? (
          <RequestDetailPage key={detail[1]} s={s} lang={lang} id={detail[1]} navigate={navigate} />
        ) : path.startsWith("/requests") ? (
          <RequestsPage s={s} lang={lang} navigate={navigate} />
        ) : path.startsWith("/check") ? (
          <CheckerPage s={s} lang={lang} />
        ) : (
          <NewRequestPage s={s} navigate={navigate} />
        )}
      </main>
    </div>
  );
}
