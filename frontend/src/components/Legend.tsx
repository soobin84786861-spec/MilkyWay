export default function Legend() {
  const items = [
    { color: 'bg-green-500', label: '안전' },
    { color: 'bg-yellow-500', label: '주의' },
    { color: 'bg-orange-500', label: '위험' },
    { color: 'bg-red-500', label: '매우 위험' },
  ];

  return (
    <div className="absolute bottom-6 left-3 z-20 rounded-2xl border border-slate-100 bg-white/92 p-3 shadow-lg backdrop-blur-sm md:bottom-6 md:left-6 md:p-4">
      <p className="mb-2 text-[11px] font-semibold uppercase tracking-wide text-slate-500 md:mb-2.5 md:text-xs">
        위험도
      </p>
      <div className="grid grid-cols-2 gap-x-4 gap-y-2 md:grid-cols-1 md:gap-x-0 md:gap-y-2">
        {items.map(({ color, label }) => (
          <div key={label} className="flex items-center gap-2">
            <span className={`h-3 w-3 flex-shrink-0 rounded-sm ${color} md:h-3.5 md:w-3.5`} />
            <span className="text-xs font-medium text-slate-700 md:text-sm">{label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
