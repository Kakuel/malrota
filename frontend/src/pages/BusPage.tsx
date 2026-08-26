import { useState, useEffect, useRef } from 'react'
import { parseConversation, recommendBuses } from '../api/conversationApi'
import { recommendSeat } from '../api/seatApi'
import { useAppState } from '../features/conversation/AppState'
import { VoicePanel, speak } from '../features/conversation/VoicePanel'
import type { BusSchedule } from '../features/conversation/types'
import './HomePage.css'

function formatTime(raw: string): string {
  if (!raw || raw.length < 12) return raw
  const hour = parseInt(raw.substring(8, 10), 10)
  const minute = raw.substring(10, 12)
  const period = hour < 12 ? '오전' : '오후'
  const h = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return minute === '00' ? `${period} ${h}시` : `${period} ${h}시 ${minute}분`
}

export function BusPage() {
  const {
    recommendations, setRecommendations, setSelectedBus, setSeat, setScreen, addMessage,
    seatPreferences, setSeatPreferences, accessibilityNeeds, setAccessibilityNeeds,
    sessionId, setSessionId, setConditions, conditions,
  } = useAppState()
  const [loading, setLoading] = useState(false)
  const announced = useRef(false)

  function appSay(t: string) {
    addMessage('app', t)
    speak(t)
  }

  // 화면 뜰 때 3개 추천 음성 안내
  useEffect(() => {
    if (recommendations.length > 0 && !announced.current) {
      announced.current = true
      const text = recommendations
        .map((r) => `${r.reason} ${formatTime(r.bus.departureTime)} 출발, ${r.bus.charge.toLocaleString()}원.`)
        .join('\n')
      appSay('추천 버스를 안내해드릴게요. ' + text + ' 어떤 버스로 하시겠어요?')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function chooseBus(bus: BusSchedule) {
    setSelectedBus(bus)
    setLoading(true)
    try {
      const seatData = await recommendSeat({
        seatPreferences: seatPreferences as any,
        accessibilityNeeds: accessibilityNeeds as any,
        busGrade: bus.grade,
        passengers: conditions?.passengers ?? 1,
      })
      setSeat(seatData)
      appSay(`${formatTime(bus.departureTime)} 출발 버스를 선택했어요. 추천 좌석은 ${seatData.bestSeat?.seatNo ?? ''}번입니다. 이 좌석으로 결제할까요?`)
      setTimeout(() => setScreen('seat'), 3000)
    } catch (e) {
      appSay('좌석 정보를 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }

  function chosenBusFromSpeech(text: string, options = recommendations) {
    if (options.length === 0) return null
    if (text.includes('저렴') || text.includes('싼') || text.includes('싸')) {
      return (options.find((r) => r.label.includes('최저가')) ?? options[0]).bus
    }
    if (text.includes('빠른') || text.includes('이른') || text.includes('첫') || text.includes('추천')) {
      return (options.find((r) => r.label.includes('추천')) ?? options[0]).bus
    }
    if (text.includes('두') || text.includes('2')) return (options[1] ?? options[0]).bus
    if (text.includes('세') || text.includes('3')) return (options[2] ?? options[0]).bus
    if (text.includes('첫') || text.includes('1')) return options[0].bus
    return null
  }

  // 음성 발화를 먼저 LLM/규칙 파서에 전달하고, 변경된 조건으로 추천을 다시 계산한다.
  async function handleUserSpeak(text: string) {
    addMessage('user', text)
    setLoading(true)
    try {
      const session = await parseConversation(text, sessionId)
      setSessionId(session.sessionId)
      setConditions(session)
      setSeatPreferences(session.seatPreferences ?? [])
      setAccessibilityNeeds(session.accessibilityNeeds ?? [])

      if (session.intent === 'CANCEL') {
        appSay('버스 선택을 취소하고 처음 화면으로 돌아갈게요.')
        setScreen('home')
        return
      }

      if (!session.departure || !session.arrival || !session.date) {
        appSay(session.clarificationPrompt ?? '출발지, 도착지, 날짜를 다시 말씀해 주세요.')
        return
      }

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

      const selected = chosenBusFromSpeech(text, nextRecommendations)
      if (selected) {
        await chooseBus(selected)
      } else if (nextRecommendations.length === 0) {
        appSay('바뀐 조건에 맞는 버스를 찾지 못했습니다. 다른 시간이나 등급을 말씀해 주세요.')
      } else {
        appSay('말씀하신 조건을 반영했습니다. 저렴한 것, 추천 시간, 또는 몇 번째 버스인지 말씀해 주세요.')
      }
    } catch {
      appSay('조건을 반영하지 못했습니다. 서버 상태를 확인해 주세요.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="phone-frame">
      <header className="home-header">
        <button type="button" className="info-button" onClick={() => setScreen('home')}>
          ← 뒤로
        </button>
      </header>

      <h1 className="home-title" style={{ fontSize: '1.4rem' }}>추천 버스를 골라주세요</h1>

      <div className="home-body">
        {recommendations.length === 0 ? (
          <p>추천할 버스가 없습니다.</p>
        ) : (
          recommendations.map((rec, i) => (
            <button
              key={i}
              type="button"
              onClick={() => chooseBus(rec.bus)}
              disabled={loading}
              style={{
                width: '100%',
                textAlign: 'left',
                background: '#fff',
                border: '2px solid #f0e6d8',
              borderRadius: '14px',
              padding: '14px',
              marginBottom: '8px',
                cursor: 'pointer',
                position: 'relative',
              }}
            >
              {/* 라벨 뱃지 */}
              <span style={{
                display: 'inline-block',
                background: '#f07f21',
                color: '#fff',
                fontSize: '0.85rem',
                fontWeight: 700,
                padding: '4px 12px',
                borderRadius: '999px',
                marginBottom: '5px',
              }}>
                {rec.label}
              </span>
              <div style={{ fontSize: '1.08rem', fontWeight: 800, color: '#2b2320' }}>
                {rec.bus.departure} → {rec.bus.arrival}
              </div>
              <div style={{ fontSize: '1rem', color: '#f07f21', marginTop: '4px' }}>
                {formatTime(rec.bus.departureTime)} 출발 · {formatTime(rec.bus.arrivalTime)} 도착
              </div>
              <div style={{ fontSize: '0.9rem', color: '#58665f', marginTop: '3px' }}>
                {rec.bus.grade} · {rec.bus.charge.toLocaleString()}원
              </div>
            </button>
          ))
        )}

        <VoicePanel onUserSpeak={handleUserSpeak} loading={loading} compact />
      </div>
    </div>
  )
}
