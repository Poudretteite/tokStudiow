import type { ReactNode, ThHTMLAttributes, TdHTMLAttributes } from 'react';

export function Table({ children, minWidth }: { children: ReactNode; minWidth?: number }) {
    return (
        <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-[15px]" style={minWidth ? { minWidth } : undefined}>
                {children}
            </table>
        </div>
    );
}

export function Th({ className = '', ...rest }: ThHTMLAttributes<HTMLTableCellElement>) {
    return (
        <th
            {...rest}
            className={`border-b border-line bg-head px-4 py-2 text-sm font-semibold whitespace-nowrap ${className}`}
        />
    );
}

export function Td({ className = '', ...rest }: TdHTMLAttributes<HTMLTableCellElement>) {
    return <td {...rest} className={`border-b border-line px-4 py-2 align-top ${className}`} />;
}

export function Tr({ children }: { children: ReactNode }) {
    return <tr className="hover:bg-page/70 [&:last-child>td]:border-b-0">{children}</tr>;
}