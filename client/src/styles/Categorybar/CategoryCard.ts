import tw from 'tailwind-styled-components';

export const CardContainer = tw.div`
  flex
  w-[800px]
  h-[200px]
  border
  my-6
  border-[#AEC1DF]
  hover:shadow-[0_0_10px3px#A6C7FF]
  duration-500
`;
export const CardText = tw.div`
  flex
  flex-col
  items-start
  p-5
`;
export const CardPrice = tw.div`
  flex
  justify-between
  font-semibold
  w-[510px]
  mt-[90px]
`;
export const Text = tw.div`
  flex  
  mt-[5px]
`;
