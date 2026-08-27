import React from 'react';

interface MallotaLogoProps {
  /** 아이콘 한 변의 크기(px). 기본 40 */
  size?: number;
  /** 버스 몸통 색. 기본 밝은 파랑 */
  busColor?: string;
  /** 주황 포인트(승객·미소·그릴 및 텍스트) 색. 기본 주황 */
  accentColor?: string;
  /** 글자(mallota)를 함께 표시할지 여부 (기본 true) */
  showText?: boolean;
  /** 스크린리더가 읽어주는 이름 */
  title?: string;
  className?: string;
}

export default function MallotaLogo({
  size = 40,
  busColor = '#3B9EFF',
  accentColor = '#FF7A1A',
  showText = true,
  title = '말로타',
  className,
}: MallotaLogoProps): JSX.Element {
  return (
    <div className={`mallota-logo-container ${className || ''}`}>
      {/* 1. 버스 캐릭터 SVG 아이콘 */}
      <div style={{
        background: '#fff8f0', 
        borderRadius: '12px', 
        padding: '2px', 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center',
        border: '1px solid #fce7d2'
      }}>
        <svg
          width={size}
          height={size}
          viewBox="0 0 120 120"
          role="img"
          aria-label={title}
          xmlns="http://www.w3.org/2000/svg"
        >
          <g
            transform="rotate(-7 60 58)"
            fill="none"
            stroke={busColor}
            strokeWidth={4.2}
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            {/* 사이드 미러 */}
            <path d="M27 52 q-7 -1 -7 6 q0 7 7 6" fill={busColor} />
            <path d="M93 52 q7 -1 7 6 q0 7 -7 6" fill={busColor} />

            {/* 몸통 */}
            <rect x="30" y="24" width="60" height="66" rx="16" />

            {/* 앞유리 창 두 개 */}
            <rect x="38" y="33" width="18" height="16" rx="5" />
            <rect x="64" y="33" width="18" height="16" rx="5" />

            {/* 창 안 승객 (주황) */}
            <path
              d="M43 49 q0 -11 4 -11 q4 0 4 11 z"
              fill={accentColor}
              stroke={accentColor}
              strokeWidth={2}
            />
            <path
              d="M69 49 q0 -11 4 -11 q4 0 4 11 z"
              fill={accentColor}
              stroke={accentColor}
              strokeWidth={2}
            />

            {/* 미소 / 범퍼 (주황) */}
            <path d="M38 55 q22 12 44 0" stroke={accentColor} strokeWidth={4.5} />

            {/* 전조등 */}
            <circle cx="43" cy="72" r="4.5" />
            <circle cx="77" cy="72" r="4.5" />

            {/* 그릴 / 코 (주황) */}
            <path d="M54 70 h12 M54 76 h12" stroke={accentColor} strokeWidth={4} />

            {/* 바퀴 */}
            <path d="M42 90 v6" strokeWidth={6} />
            <path d="M78 90 v6" strokeWidth={6} />
          </g>
        </svg>
      </div>

      {/* 2. Dancing Script 폰트의 mallota 텍스트 */}
      {showText && (
        <span className="mallota-logo-text" style={{ color: accentColor }}>
          mallota
        </span>
      )}
    </div>
  );
}