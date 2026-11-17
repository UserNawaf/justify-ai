/** Small inline icons (no icon library needed). */
const base = { width: 18, height: 18, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 2,
  strokeLinecap: "round" as const, strokeLinejoin: "round" as const, "aria-hidden": true };

export const BuildingIcon = () => (
  <svg {...base} width={22} height={22}>
    <rect x="4" y="3" width="16" height="18" rx="1" />
    <path d="M9 21v-4h6v4M8 7h2M14 7h2M8 11h2M14 11h2" />
  </svg>
);
export const UserIcon = () => (
  <svg {...base} width={24} height={24}>
    <circle cx="12" cy="12" r="10" />
    <circle cx="12" cy="10" r="3" />
    <path d="M6.2 18.4a7 7 0 0 1 11.6 0" />
  </svg>
);
export const SendIcon = () => (
  <svg {...base}>
    <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7z" />
  </svg>
);
export const SaveIcon = () => (
  <svg {...base}>
    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
    <path d="M17 21v-8H7v8M7 3v5h8" />
  </svg>
);
export const ChevronIcon = () => (
  <svg {...base} width={14} height={14} className="chev">
    <path d="m9 18 6-6-6-6" />
  </svg>
);
export const SparkIcon = () => (
  <svg {...base} width={16} height={16}>
    <path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6" />
  </svg>
);
