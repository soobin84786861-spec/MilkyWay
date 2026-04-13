export default function Legend() {
  const items = [
    { color: 'bg-green-500', label: '안전' },
    { color: 'bg-yellow-500', label: '주의' },
    { color: 'bg-orange-500', label: '위험' },
    { color: 'bg-red-500', label: '매우 위험' },
  ];

  return (
    <div className="absolute bottom-6 left-6 bg-white/90 backdrop-blur-sm rounded-2xl shadow-lg border border-slate-100 p-4 z-20">
      <p className="text-xs font-semibold text-slate-500 mb-2.5 uppercase tracking-wide">위험도</p>
      <div className="flex flex-col gap-2">
        {items.map(({ color, label }) => (
          <div key={label} className="flex items-center gap-2.5">
            <span className={`w-3.5 h-3.5 rounded-sm ${color} flex-shrink-0`} />
            <span className="text-sm text-slate-700 font-medium">{label}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
