import { useEffect, useRef, type ReactNode } from 'react';

interface Props {
    open: boolean;
    onClose: () => void;
    title: string;
    children: ReactNode;
}

/**
 * Okno modalne na natywnym <dialog>: przeglądarka sama zatrzymuje fokus w oknie,
 * zamyka je klawiszem Esc i blokuje resztę strony.
 */
export function Dialog({ open, onClose, title, children }: Props) {
    const ref = useRef<HTMLDialogElement>(null);

    useEffect(() => {
        const dialog = ref.current;
        if (!dialog) {
            return;
        }
        if (open && !dialog.open) {
            dialog.showModal();
        } else if (!open && dialog.open) {
            dialog.close();
        }
    }, [open]);

    return (
        <dialog
            ref={ref}
            onClose={onClose}
            aria-labelledby="dialog-title"
            className="m-auto w-[calc(100%-2rem)] max-w-md border border-line bg-white p-0 text-ink backdrop:bg-ink/40"
        >
            <h2 id="dialog-title" className="border-b border-line bg-panel-head px-4 py-2.5 font-semibold">
                {title}
            </h2>
            <div className="p-4">{children}</div>
        </dialog>
    );
}