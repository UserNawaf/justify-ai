import { useEffect, useState } from "react";
import { loadProfile, setLanguage, type Profile } from "./api";
import { ProfileDialog, Shell } from "./components/Layout";
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
      return (localStorage.getItem("lang") as Lang) || "en";
    } catch {
      return "en";
    }
  });
  const s = t(lang);
  const [path, navigate] = useHashRoute();
  const [profile, setProfile] = useState<Profile>(loadProfile);
  const [profileOpen, setProfileOpen] = useState(false);
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

  return (
    <Shell s={s} path={path} onToggleLang={() => setLang(lang === "ar" ? "en" : "ar")} onProfile={() => setProfileOpen(true)}>
      {detail ? (
        <RequestDetailPage key={detail[1]} s={s} lang={lang} id={detail[1]} />
      ) : path.startsWith("/requests") ? (
        <RequestsPage s={s} lang={lang} navigate={navigate} />
      ) : path.startsWith("/check") ? (
        <CheckerPage s={s} lang={lang} />
      ) : (
        <NewRequestPage s={s} lang={lang} profile={profile} onNeedProfile={() => setProfileOpen(true)} navigate={navigate} />
      )}
      {profileOpen && (
        <ProfileDialog s={s} onClose={(p) => { if (p) setProfile(p); setProfileOpen(false); }} />
      )}
    </Shell>
  );
}
