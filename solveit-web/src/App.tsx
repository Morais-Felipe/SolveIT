import { Route, Routes } from 'react-router-dom'
import { AppLayout } from '@/layout/AppLayout'
import { CadastroPage } from '@/pages/CadastroPage'
import { ChamadosPage } from '@/pages/ChamadosPage'
import { EmBreve, NaoEncontrado } from '@/pages/EmBreve'
import { LoginPage } from '@/pages/LoginPage'
import { RedirecionarParaInicial, RotaPorPerfil, RotaProtegida, SomenteAnonimo } from '@/routes/guards'

/**
 * Mapa de rotas. O Router e o AuthProvider ficam em main.tsx, para que os testes
 * possam montar o App dentro de um MemoryRouter.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<SomenteAnonimo><LoginPage /></SomenteAnonimo>} />
      <Route path="/cadastro" element={<SomenteAnonimo><CadastroPage /></SomenteAnonimo>} />

      <Route element={<RotaProtegida><AppLayout /></RotaProtegida>}>
        <Route index element={<RedirecionarParaInicial />} />
        <Route path="chamados" element={<ChamadosPage />} />
        <Route
          path="chamados/novo"
          element={
            <RotaPorPerfil perfis={['SOLICITANTE']}>
              <EmBreve titulo="Abrir chamado" plano="06-02" />
            </RotaPorPerfil>
          }
        />
        <Route
          path="fila"
          element={
            <RotaPorPerfil perfis={['ATENDENTE']}>
              <EmBreve titulo="Fila de atendimento" plano="06-04" />
            </RotaPorPerfil>
          }
        />
        <Route
          path="dashboard"
          element={
            <RotaPorPerfil perfis={['ADMIN', 'ATENDENTE']}>
              <EmBreve titulo="Dashboard" plano="06-05" />
            </RotaPorPerfil>
          }
        />
        <Route
          path="usuarios"
          element={
            <RotaPorPerfil perfis={['ADMIN']}>
              <EmBreve titulo="Usuários" plano="06-05" />
            </RotaPorPerfil>
          }
        />
        <Route
          path="categorias"
          element={
            <RotaPorPerfil perfis={['ADMIN']}>
              <EmBreve titulo="Categorias" plano="06-05" />
            </RotaPorPerfil>
          }
        />
      </Route>

      <Route path="*" element={<NaoEncontrado />} />
    </Routes>
  )
}
