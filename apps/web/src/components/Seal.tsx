export function Seal({ size = 44 }: { size?: number }) {
    return (
        <svg viewBox="0 0 48 48" width={size} height={size} aria-hidden="true">
            <circle cx="24" cy="24" r="22.5" fill="none" stroke="currentColor" strokeWidth="1.5" />
            <circle cx="24" cy="24" r="18.5" fill="none" stroke="currentColor" strokeWidth="0.75" />
            <text
                x="24"
                y="29.5"
                textAnchor="middle"
                fontFamily='"Source Sans 3", sans-serif'
                fontWeight="700"
                fontSize="15"
                fill="currentColor"
            >
                tS
            </text>
        </svg>
    );
}