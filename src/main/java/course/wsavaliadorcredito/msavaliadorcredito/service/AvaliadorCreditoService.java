package course.wsavaliadorcredito.msavaliadorcredito.service;

import course.wsavaliadorcredito.msavaliadorcredito.domain.model.*;
import course.wsavaliadorcredito.msavaliadorcredito.ex.ErroSolicitacaoException;
import course.wsavaliadorcredito.msavaliadorcredito.infra.client.CartaoResourceClient;
import course.wsavaliadorcredito.msavaliadorcredito.infra.client.ClientResourceClient;
import course.wsavaliadorcredito.msavaliadorcredito.infra.mqueue.SolicitacaoEmissaoCartaoPublish;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AvaliadorCreditoService {

    private final ClientResourceClient clientesClient;
    private final CartaoResourceClient cartaoResourceClient;
    private final SolicitacaoEmissaoCartaoPublish emissaoCartaoPublish;


    public SituacaoCliente obterSituacaoCliente(String cpf) {

        ResponseEntity<DadosCliente> dadosClienteResponse = clientesClient.dadosCliente(cpf);
        ResponseEntity<List<CartaoCliente>> cartoesResponse = cartaoResourceClient.getCartoesByCliente(cpf);
        return SituacaoCliente
                .builder()
                .cliente(dadosClienteResponse.getBody())
                .cartoes(cartoesResponse.getBody())
                .build();
    }

    public RetornoAvalicaoCliente realizarAvaliacao(String cpf, Long renda){
        ResponseEntity<DadosCliente> dadosClienteResponse = clientesClient.dadosCliente(cpf);
        ResponseEntity<List<Cartao>> cartoesResponse = cartaoResourceClient.getCartoesRendaAteh(renda);
        List<Cartao> cartaos = cartoesResponse.getBody();
       var listaCartoesAprovados = cartaos.stream().map( cartoes -> {

            DadosCliente dadosCliente = dadosClienteResponse.getBody();
            BigDecimal limiteBasico = cartoes.getLimiteBasico();
            BigDecimal rendaBD = BigDecimal.valueOf(renda);
            BigDecimal idadeBD = BigDecimal.valueOf(dadosCliente.getIdade());
            var fator =   idadeBD.divide(BigDecimal.valueOf(10));
            BigDecimal limiteAprovado = fator.multiply(limiteBasico);

            CartaoAprovado aprovado = new CartaoAprovado();
            aprovado.setCartao(cartoes.getNome());
            aprovado.setBandeira(cartoes.getBandeira());
            aprovado.setLimiteAprovado(limiteAprovado);

            return aprovado;
        }).collect(Collectors.toList());

       return new RetornoAvalicaoCliente(listaCartoesAprovados);

    }

    public ProtocoloSolicitacaoCartao solicitarEmissaoCartao(DadosSolicitacaoEmissaoCartao dados){
        try {
            emissaoCartaoPublish.solicitarCartao(dados);
            var protocolo = UUID.randomUUID().toString();
            return new ProtocoloSolicitacaoCartao(protocolo);
        }catch (Exception e){
            throw new ErroSolicitacaoException(e.getMessage());
        }
    }
}
