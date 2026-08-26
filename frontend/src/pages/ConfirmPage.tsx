import { useEffect, useRef } from 'react'
import { parseConversation, recommendBuses } from '../api/conversationApi'
import { recommendSeat } from '../api/seatApi'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
import './HomePage.css'

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${period} ${h}:${minute}`
}

function formatDate(raw: string): string {
  if (!raw || raw.length < 8) return raw
  return `${raw.substring(0, 4)}.${raw.substring(4, 6)}.${raw.substring(6, 8)}`
}

export function ConfirmPage() {
  const {
    selectedBus, seat, selectedSeatNo,
    setScreen, addMessage, addBooking, resetMessages,
    setSelectedBus, setSeat, setSelectedSeatNo, sessionId, setSessionId,
    conditions, setConditions, setRecommendations,
    seatPreferences, setSeatPreferences, accessibilityNeeds, setAccessibilityNeeds,
  } = useAppState()

  const announced = useRef(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  const seatNo = selectedSeatNo ?? seat?.bestSeat?.seatNo ?? ''

  // 화면 뜰 때 승차권 안내
  useEffect(() => {
    if (selectedBus && !announced.current) {
      announced.current = true
      appSay(`${selectedBus.departure}에서 ${selectedBus.arrival}로 가는 ${formatTime(selectedBus.departureTime)} 출발 버스가 준비되었습니다. 좌석은 ${seatNo}번입니다. 결제할까요?`)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function pay() {
    if (!selectedBus) return
    addBooking({ bus: selectedBus, seatNo, id: Date.now().toString() })
    appSay('결제가 완료되었습니다. 안전한 여행 되세요.')
    setTimeout(() => {
      setSelectedBus(null)
      setSeat(null)
      setSelectedSeatNo(null)
      setSessionId(null)
      setConditions(null)
      resetMessages()
      setScreen('home')
    }, 2000)
  }

  function sameValues(first: string[], second: string[]) {
    return first.length === second.length && first.every((value) => second.includes(value))
  }

  function busConditionsChanged(next: NonNullable<typeof conditions>) {
    if (!conditions) return false
    return conditions.departure !== next.departure
      || conditions.arrival !== next.arrival
      || conditions.date !== next.date
      || conditions.departureTime !== next.departureTime
      || conditions.timePreference !== next.timePreference
      || conditions.servicePreference !== next.servicePreference
      || conditions.busGradePreference !== next.busGradePreference
  }

  async function handleUserSpeak(text: string) {
    addMessage('user', text)
    try {
      const session = await parseConversation(text, sessionId)
      setSessionId(session.sessionId)

      if (session.intent === 'CANCEL' || text.includes('뒤로')) {
        appSay('결제를 취소하고 좌석 선택 화면으로 돌아갈게요.')
        setScreen('seat')
        return
      }

      const nextSeatPreferences = session.seatPreferences ?? []
      const nextAccessibilityNeeds = session.accessibilityNeeds ?? []
      const preferencesChanged = !sameValues(nextSeatPreferences, seatPreferences)
        || !sameValues(nextAccessibilityNeeds, accessibilityNeeds)
      const searchConditionsChanged = busConditionsChanged(session)
      setConditions(session)
      setSeatPreferences(nextSeatPreferences)
      setAccessibilityNeeds(nextAccessibilityNeeds)

      if (searchConditionsChanged && session.departure && session.arrival && session.date) {
        const nextRecommendations = await recommendBuses({
          departure: session.departure,
          arrival: session.arrival,
          date: session.date,
          departureTime: session.departureTime,
          timePreference: session.timePreference,
          servicePreference: session.servicePreference,
          busGradePreference: session.busGradePreference,
        })
        setRecommendations(nextRecommendations)
        appSay('바뀐 출발 조건에 맞춰 버스를 다시 찾아볼게요.')
        setScreen('bus')
        return
      }

      if (preferencesChanged && selectedBus) {
        const updatedSeat = await recommendSeat({
          seatPreferences: nextSeatPreferences as any,
          accessibilityNeeds: nextAccessibilityNeeds as any,
          busGrade: selectedBus.grade,
          passengers: session.passengers,
        })
        setSeat(updatedSeat)
        setSelectedSeatNo(null)
        appSay('바뀐 좌석 조건을 반영했습니다. 좌석을 다시 확인해 주세요.')
        setScreen('seat')
        return
      }

      if (text.includes('결제') || text.includes('네') || text.includes('할게') || text.includes('좋아') || text.includes('그래') || text.includes('예')) {
        pay()
      } else {
        appSay('결제하시려면 결제할게요, 조건을 바꾸려면 원하는 시간이나 좌석을 말씀해 주세요.')
      }
    } catch {
      appSay('말씀하신 조건을 처리하지 못했습니다. 서버 상태를 확인해 주세요.')
    }
  }

  if (!selectedBus) {
    return (
      <div className="phone-frame">
        <p>예약 정보가 없습니다.</p>
        <button className="send-button" onClick={() => setScreen('home')}>홈으로</button>
      </div>
    )
  }

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('seat')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>예약 확인</h1>

      <div className="home-body">
        <div style={{
          border: '2px dashed #f07f21',
          borderRadius: '16px',
          padding: '16px',
          background: '#fff8f0',
        }}>
          <div style={{ textAlign: 'center', fontSize: '1.15rem', fontWeight: 800, color: '#f07f21' }}>
            🎫 승차권
          </div>
          <div style={{ borderTop: '1px solid #f0d5b8', margin: '10px 0' }} />

          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '10px' }}>
            <div style={{ textAlign: 'center' }}>
              <div style={{ color: '#58665f', fontSize: '0.9rem' }}>출발</div>
              <div style={{ fontSize: '1.15rem', fontWeight: 800 }}>{selectedBus.departure}</div>
              <div style={{ color: '#f07f21' }}>{formatTime(selectedBus.departureTime)}</div>
            </div>
            <div style={{ fontSize: '1.5rem', alignSelf: 'center' }}>→</div>
            <div style={{ textAlign: 'center' }}>
              <div style={{ color: '#58665f', fontSize: '0.9rem' }}>도착</div>
              <div style={{ fontSize: '1.15rem', fontWeight: 800 }}>{selectedBus.arrival}</div>
              <div style={{ color: '#f07f21' }}>{formatTime(selectedBus.arrivalTime)}</div>
            </div>
          </div>

          <div style={{ borderTop: '1px dashed #f0d5b8', margin: '10px 0' }} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '7px', fontSize: '0.92rem' }}>
            <div><span style={{ color: '#58665f' }}>날짜 </span>{formatDate(selectedBus.departureTime)}</div>
            <div><span style={{ color: '#58665f' }}>등급 </span>{selectedBus.grade}</div>
            <div><span style={{ color: '#58665f' }}>좌석 </span><b style={{ color: '#f07f21' }}>{seatNo}</b></div>
            <div><span style={{ color: '#58665f' }}>요금 </span><b>{selectedBus.charge.toLocaleString()}원</b></div>
          </div>
        </div>

        <button type="button" className="send-button" onClick={pay} style={{ marginTop: '10px' }}>
          결제하기
        </button>

        <VoicePanel onUserSpeak={handleUserSpeak} compact />
      </div>
    </div>
  )
}
