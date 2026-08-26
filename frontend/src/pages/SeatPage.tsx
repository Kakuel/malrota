import { useState } from 'react'
import { parseConversation } from '../api/conversationApi'
import { recommendSeat } from '../api/seatApi'
import { SeatMap } from '../features/conversation/SeatMap'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
import './HomePage.css'


export function SeatPage() {
  const {
    seat, selectedBus, selectedSeatNo, setSelectedSeatNo, setSeat, setScreen, addMessage,
    sessionId, setSessionId, setConditions,
    seatPreferences, setSeatPreferences, accessibilityNeeds, setAccessibilityNeeds,
  } = useAppState()
  const [selecting, setSelecting] = useState(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  function sameValues(first: string[], second: string[]) {
    return first.length === second.length && first.every((value) => second.includes(value))
  }

  async function handleUserSpeak(text: string) {
    addMessage('user', text)
    if (!seat || !seat.bestSeat) return

    try {
      const session = await parseConversation(text, sessionId)
      setSessionId(session.sessionId)
      setConditions(session)

      if (session.intent === 'CANCEL') {
        appSay('좌석 선택을 취소하고 버스 목록으로 돌아갈게요.')
        setScreen('bus')
        return
      }

      const nextSeatPreferences = session.seatPreferences ?? []
      const nextAccessibilityNeeds = session.accessibilityNeeds ?? []
      const preferencesChanged = !sameValues(nextSeatPreferences, seatPreferences)
        || !sameValues(nextAccessibilityNeeds, accessibilityNeeds)
      setSeatPreferences(nextSeatPreferences)
      setAccessibilityNeeds(nextAccessibilityNeeds)

      if (preferencesChanged && selectedBus) {
        const updatedSeat = await recommendSeat({
          seatPreferences: nextSeatPreferences as any,
          accessibilityNeeds: nextAccessibilityNeeds as any,
          busGrade: selectedBus.grade,
          passengers: session.passengers,
        })
        setSeat(updatedSeat)
        setSelectedSeatNo(null)
        appSay(`말씀하신 조건을 반영했어요. 추천 좌석은 ${updatedSeat.bestSeat?.seatNo ?? ''}번입니다.`)
        return
      }

      if (text.includes('추천') || text.includes('예약') || text.includes('이걸로') || text.includes('그걸로') || text.includes('네') || text.includes('좋아') || text.includes('결제')) {
        setTimeout(() => setScreen('confirm'), 600)
      } else {
        appSay('원하시는 좌석 조건을 말씀하시거나, 이 좌석으로 예약해 달라고 말씀해 주세요.')
      }
    } catch {
      appSay('좌석 조건을 반영하지 못했습니다. 서버 상태를 확인해 주세요.')
    }
  }

  // 화면 뜰 때 한 번 안내
  
  if (!seat || !seat.bestSeat) {
    return (
      <div className="phone-frame">
        <p>좌석 정보가 없습니다.</p>
        <button className="send-button" onClick={() => setScreen('bus')}>뒤로</button>
      </div>
    )
  }

  const finalSeatNo = selectedSeatNo ?? seat.bestSeat.seatNo

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('bus')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>좌석 선택</h1>

      <div className="home-body">
        <p style={{ fontSize: '1rem', fontWeight: 700, margin: '4px 0 8px' }}>
          추천 좌석: <span style={{ color: '#f07f21' }}>{finalSeatNo}</span>
        </p>
        <ul style={{ margin: '0 0 8px', paddingLeft: '20px', fontSize: '0.92rem' }}>
          {seat.reasons.map((r, i) => (<li key={i}>{r}</li>))}
        </ul>

        {!selecting ? (
          <button type="button" className="send-button" onClick={() => setSelecting(true)} style={{ marginBottom: '8px' }}>
            다른 좌석 선택하기
          </button>
        ) : (
          <p style={{ color: '#f07f21', margin: '0 0 6px', fontSize: '0.92rem' }}>앉고 싶은 좌석을 눌러주세요.</p>
        )}

        <SeatMap
          seats={seat.allSeats}
          recommendedNo={seat.bestSeat.seatNo}
          alternativeNos={seat.alternatives.map((s) => s.seatNo)}
          selectedNo={selectedSeatNo ?? undefined}
          onSelect={selecting ? (selected) => setSelectedSeatNo(selected.seatNo) : undefined}
        />

        <button type="button" className="send-button" onClick={() => setScreen('confirm')} style={{ marginTop: '10px' }}>
          이 좌석으로 예약하기
        </button>

        <VoicePanel onUserSpeak={handleUserSpeak} compact />
      </div>
    </div>
  )
}
