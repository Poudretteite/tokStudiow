export function Spinner({ size = 'md' }: { size?: 'sm' | 'md' }) {
    const dimensions = size === 'sm' ? 'size-4' : 'size-6';
    return (
        <span
            role="status"
            aria-label="Wczytywanie"
            className={`inline-block ${dimensions} animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none`}
        />
    );
}